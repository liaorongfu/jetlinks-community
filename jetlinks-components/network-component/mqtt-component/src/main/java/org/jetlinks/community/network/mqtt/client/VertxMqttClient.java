package org.jetlinks.community.network.mqtt.client;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.buffer.Buffer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetlinks.community.network.DefaultNetworkType;
import org.jetlinks.community.network.NetworkType;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.core.message.codec.SimpleMqttMessage;
import org.jetlinks.core.topic.Topic;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 使用Vertx，MQTT Client。
 * 基于Vert.x实现的Mqtt客户端，提供消息订阅和发布功能。
 * @author zhouhao
 * @since 1.0
 */
@Slf4j
public class VertxMqttClient implements MqttClient {

    @Getter
    private io.vertx.mqtt.MqttClient client;
    // 主题订阅管理
    private final Topic<Tuple3<String, FluxSink<MqttMessage>, Integer>> subscriber = Topic.createRoot();
    // 客户端ID
    private final String id;
    // 加载状态标识，用于控制订阅操作
    private volatile boolean loading;
    // 加载成功后的回调监听器列表
    private final List<Runnable> loadSuccessListener = new CopyOnWriteArrayList<>();

    // 订阅主题前缀
    @Setter
    private String topicPrefix;
    /**
     * 设置加载状态。
     * 该方法用于更新当前的加载状态，并在加载结束时通知所有监听者。
     * @param loading 是否正在加载。如果为true，表示正在加载；如果为false，表示加载结束。
     */
    public void setLoading(boolean loading) {
        this.loading = loading; // 更新加载状态
        if (!loading) { // 当加载结束时
            loadSuccessListener.forEach(Runnable::run); // 执行所有加载成功后的监听动作
            loadSuccessListener.clear(); // 清空监听者列表，准备下一次加载
        }
    }

    /**
     * 获取当前加载状态。
     * @return 是否正在加载
     */
    public boolean isLoading() {
        return loading;
    }
    /**
     * 构造函数，初始化客户端ID。
     * @param id 客户端唯一标识
     */
    public VertxMqttClient(String id) {
        this.id = id;
    }
    /**
     * 设置MQTT客户端实例，并进行相关事件处理器的绑定。
     * 此方法会首先检查是否有已存在的客户端实例，如果有且与传入的客户端实例不同，则尝试断开连接。
     * 然后，绑定关闭事件处理器和发布事件处理器，并根据加载状态重新订阅或立即订阅主题。
     *
     * @param client Vert.x MQTT客户端实例，用于消息的发送和接收。
     */
    public void setClient(io.vertx.mqtt.MqttClient client) {
        // 当客户端实例变更时，处理旧客户端的断开连接
        if (this.client != null && this.client != client) {
            try {
                this.client.disconnect();
            } catch (Exception ignore) {
                // 忽略断开连接异常，确保后续流程执行
            }
        }
        this.client = client;

        // 绑定MQTT客户端的关闭和发布事件的处理器
        client
            .closeHandler(nil -> log.debug("mqtt client [{}] closed", id)) // 处理客户端关闭事件，记录日志
            .publishHandler(msg -> { // 处理接收到的发布消息
                try {
                    // 将Vert.x MQTT消息转换为自定义消息格式
                    MqttMessage mqttMessage = SimpleMqttMessage
                        .builder()
                        .messageId(msg.messageId())
                        .topic(msg.topicName())
                        .payload(msg.payload().getByteBuf())
                        .dup(msg.isDup())
                        .retain(msg.isRetain())
                        .qosLevel(msg.qosLevel().value())
                        .properties(msg.properties())
                        .build();
                    log.debug("handle mqtt message \n{}", mqttMessage);
                    // 分发消息给所有订阅了该主题的sink
                    subscriber
                        .findTopic(msg.topicName().replace("#", "**").replace("+", "*"))
                        .flatMapIterable(Topic::getSubscribers)
                        .subscribe(sink -> {
                            try {
                                sink.getT2().next(mqttMessage); // 将消息发送给订阅者
                            } catch (Exception e) {
                                log.error("handle mqtt message error", e);
                            }
                        });
                } catch (Throwable e) {
                    log.error("handle mqtt message error", e);
                }
            });

        // 根据加载状态进行相应操作：加载中则添加重新订阅的监听，否则立即重新订阅
        if (loading) {
            loadSuccessListener.add(this::reSubscribe);
        } else if (isAlive()) {
            reSubscribe();
        }

    }

    /**
     * 重新订阅所有主题。
     * 此方法不接受参数，也不返回任何值。
     * 它首先收集所有需要重新订阅的主题及其相应的QoS等级，然后通过MQTT客户端对这些主题进行订阅。
     */
    private void reSubscribe() {
        // 收集并过滤出有订阅者的主题，同时构建主题和QoS等级的映射
        subscriber
            .getAllSubscriber()
            .filter(topic -> topic.getSubscribers().size() > 0)
            .collectMap(topic -> getCompleteTopic(convertMqttTopic(topic.getSubscribers().iterator().next().getT1())),
                        topic -> topic.getSubscribers().iterator().next().getT3())
            .filter(MapUtils::isNotEmpty) // 确保映射不为空
            .subscribe(topics -> {
                log.debug("subscribe mqtt topic {}", topics); // 记录订阅的主题
                // 对收集到的主题进行订阅操作
                client.subscribe(topics);
            });
    }

    /**
     * 将MQTT主题转换为完全限定主题。
     * 这个方法主要用于将原始的MQTT主题根据特定规则转换成完全限定主题。
     * 其中，规则包括将双星号（**）替换为通配符号（#），将单星号（*）替换为加号（+）。
     * @param topic 原始主题，即需要进行转换的主题字符串。
     * @return 完全限定主题，即经过转换后的主题字符串。
     */
    private String convertMqttTopic(String topic) {
        // 替换双星号为通配符号
        return topic.replace("**", "#").replace("*", "+");
    }

    /**
     * 解析并适配主题，支持EMQ X的共享订阅。
     * 主要针对特定前缀的主题进行解析和适配，包括共享订阅主题和队列主题。
     *
     * @param topic 待解析的主题字符串。
     *               如果主题以"$share"开头，表示这是一个共享订阅主题，
     *               需要移除前缀并重新拼接以适配EMQ X的共享订阅模式。
     *               如果主题以"$queue"开头，表示这是一个队列主题，需要移除前缀。
     * @return 解析后的主题字符串。
     *         对于共享订阅主题，返回适配后的主题；
     *         对于队列主题，返回移除前缀后的主题；
     *         对于其他类型的主题，返回原主题。
     */
    protected String parseTopic(String topic) {
        // 适配EMQ X的共享订阅主题
        if (topic.startsWith("$share")) {
            topic = Stream.of(topic.split("/"))
                          .skip(2) // 跳过分享名称和主题本身，保留实际主题部分
                          .collect(Collectors.joining("/", "/", ""));
        } else if (topic.startsWith("$queue")) { // 移除队列主题的"$queue"前缀
            topic = topic.substring(6);
        }
        // 移除多余的"/"前缀
        if (topic.startsWith("//")) {
            return topic.substring(1);
        }
        return topic;
    }


    /**
     * 获取完整的topic，包括主题前缀。
     * 如果已定义了主题前缀，则将该前缀与给定的主题合并以形成完整的主题。
     *
     * @param topic 原始主题，不包含主题前缀。
     * @return 完整主题，如果设置了主题前缀，则返回包含前缀的主题；如果没有设置前缀，则直接返回原始主题。
     */
    protected String getCompleteTopic(String topic) {
        // 检查主题前缀是否为空，若为空则直接返回原始主题
        if (StringUtils.isEmpty(topicPrefix)) {
            return topic;
        }
        // 否则，将主题前缀与原始主题合并并返回
        return topicPrefix.concat(topic);
    }

    /**
     * 订阅指定的主题列表。
     * 此方法会针对提供的主题列表中的每个主题执行订阅操作，并返回一个消息流，该消息流会接收到订阅的主题发布的消息。
     *
     * @param topics 主题列表，订阅操作将针对此列表中的每个主题执行。
     * @param qos 质量等级，指定订阅的消息质量等级。
     * @return 返回一个Flux<MqttMessage>消息流，用于接收订阅的主题发布的消息。
     */
    @Override
    public Flux<MqttMessage> subscribe(List<String> topics, int qos) {
        return Flux.create(sink -> {
            // 创建一个复合可清理资源，用于管理订阅操作的清理逻辑。
            Disposable.Composite composite = Disposables.composite();

            for (String topic : topics) {
                // 处理并转换主题格式。
                String realTopic = parseTopic(topic);
                String completeTopic = getCompleteTopic(topic);

                // 为每个主题创建一个订阅管理主题。
                Topic<Tuple3<String, FluxSink<MqttMessage>, Integer>> sinkTopic = subscriber
                    .append(realTopic
                                .replace("#", "**")
                                .replace("+", "*"));

                // 记录订阅信息，包括主题、消息sink和QoS等级。
                Tuple3<String, FluxSink<MqttMessage>, Integer> topicQos = Tuples.of(topic, sink, qos);

                // 判断是否为首次订阅该主题。
                boolean first = sinkTopic.getSubscribers().size() == 0;

                // 订阅主题，并添加清理逻辑，用于取消订阅。
                sinkTopic.subscribe(topicQos);
                composite.add(() -> {
                    // 如果存在有效订阅且客户端仍处于活动状态，则取消订阅。
                    if (sinkTopic.unsubscribe(topicQos).size() > 0 && isAlive()) {
                        client.unsubscribe(convertMqttTopic(completeTopic), result -> {
                            if (result.succeeded()) {
                                log.debug("unsubscribe mqtt topic {}", completeTopic);
                            } else {
                                log.debug("unsubscribe mqtt topic {} error", completeTopic, result.cause());
                            }
                        });
                    }
                });

                // 如果客户端处于活动状态且是首次订阅，则执行真正的订阅操作。
                if (isAlive() && first) {
                    log.debug("subscribe mqtt topic {}", completeTopic);
                    client.subscribe(convertMqttTopic(completeTopic), qos, result -> {
                        // 若订阅失败，则将错误信息发送至消息流的sink。
                        if (!result.succeeded()) {
                            sink.error(result.cause());
                        }
                    });
                }
            }

            // 为sink绑定清理逻辑，用于在取消订阅时清理资源。
            sink.onDispose(composite);

        });
    }

    /**
     * 发布消息到指定主题。
     * 此方法将给定的消息发布到MQTT的主题上，并通过Mono<Void>表示异步的发布操作完成情况。
     * @param message 待发布的消息，包含消息体和相关属性（主题、质量等级、是否重复等）。
     * @return 空的Mono<Void>，表示发布操作完成。如果操作成功，Mono将会完成；如果操作失败，Mono将会错误终止。
     */
    private Mono<Void> doPublish(MqttMessage message) {
        return Mono.create((sink) -> {
            // 获取消息体并封装到Buffer中
            ByteBuf payload = message.getPayload();
            Buffer buffer = Buffer.buffer(payload);

            // 发布消息到指定主题
            client.publish(message.getTopic(),
                           buffer,
                           MqttQoS.valueOf(message.getQosLevel()),
                           message.isDup(),
                           message.isRetain(),
                           result -> {
                               try {
                                   // 处理发布结果
                                   if (result.succeeded()) {
                                       log.info("publish mqtt [{}] message success: {}", client.clientId(), message);
                                       sink.success(); // 操作成功，通知Mono完成
                                   } else {
                                       log.info("publish mqtt [{}] message error : {}", client.clientId(), message, result.cause());
                                       sink.error(result.cause()); // 操作失败，通知Mono错误终止
                                   }
                               } finally {
                                   // 无论成功或失败，都释放消息体资源
                                   ReferenceCountUtil.safeRelease(payload);
                               }
                           });
        });
    }


    /**
     * 发布消息。
     * 如果客户端正处于加载状态，则会在加载完成后发布消息。
     * @param message 待发布的消息，类型为MqttMessage，包含消息体和相关属性。
     * @return 空的Mono，表示发布操作完成。Mono<Void>在响应式编程中表示无返回值的异步操作。
     */
    @Override
    public Mono<Void> publish(MqttMessage message) {
        // 如果客户端正在加载，则异步等待加载完成后再发布消息
        if (loading) {
            return Mono.create(sink -> {
                // 将发布操作添加到加载成功后的回调队列中
                loadSuccessListener.add(() -> {
                    // 执行实际的消息发布操作，并处理成功或错误的情况
                    doPublish(message)
                        .doOnSuccess(sink::success) // 当消息发布成功时，通知Mono的订阅者操作成功
                        .doOnError(sink::error) // 当消息发布失败时，通知Mono的订阅者操作失败
                        .subscribe();
                });
            });
        }
        // 如果客户端未在加载状态，直接执行消息发布操作
        return doPublish(message);
    }


    /**
     * 获取网络连接的ID。
     *
     * @return 返回网络连接的唯一标识符。
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * 获取网络连接的类型。
     *
     * @return 返回网络连接的类型，这里固定为MQTT客户端。
     */
    @Override
    public NetworkType getType() {
        return DefaultNetworkType.MQTT_CLIENT;
    }

    /**
     * 关闭网络连接。
     * 主动将网络连接状态设置为非加载状态，并尝试断开连接，如果客户端连接存在的话。
     */
    @Override
    public void shutdown() {
        loading = false;
        if (isAlive()) {
            try {
                client.disconnect(); // 尝试断开客户端连接
            } catch (Exception ignore) {
                // 忽略断开连接时可能出现的异常
            }
            client = null; // 清空客户端实例
        }
    }

    /**
     * 检查网络连接是否存活。
     *
     * @return 如果客户端实例不为空且客户端处于连接状态，则返回true；否则返回false。
     */
    @Override
    public boolean isAlive() {
        return client != null && client.isConnected();
    }

    /**
     * 检查网络连接是否设置为自动重载。
     *
     * @return 此方法固定返回true，表示网络连接总是设置为自动重载。
     */
    @Override
    public boolean isAutoReload() {
        return true;
    }


}
