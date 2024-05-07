package org.jetlinks.community.network.mqtt.gateway.device;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.hswebframework.web.logger.ReactiveLogger;
import org.jetlinks.community.network.mqtt.gateway.device.session.MqttClientSession;
import org.jetlinks.core.ProtocolSupport;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.device.session.DeviceSessionManager;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.core.route.MqttRoute;
import org.jetlinks.core.utils.TopicUtils;
import org.jetlinks.community.gateway.AbstractDeviceGateway;
import org.jetlinks.community.gateway.GatewayState;
import org.jetlinks.community.network.mqtt.client.MqttClient;
import org.jetlinks.community.network.mqtt.gateway.device.session.UnknownDeviceMqttClientSession;
import org.jetlinks.community.gateway.DeviceGatewayHelper;
import org.jetlinks.supports.server.DecodedClientMessageHandler;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT Client 设备网关，使用网络组件中的MQTT Client来处理设备数据
 *
 * @author zhouhao
 * @since 1.0
 */
@Slf4j
public class MqttClientDeviceGateway extends AbstractDeviceGateway {

    final MqttClient mqttClient; // MQTT客户端实例

    private final DeviceRegistry registry; // 设备注册表

    private Mono<ProtocolSupport> protocol; // 协议支持的Mono对象

    private Mono<DeviceMessageCodec> codecMono; // 编解码器的Mono对象

    private final DeviceGatewayHelper helper; // 设备网关辅助类

    private final Map<RouteKey, Tuple2<Integer, Disposable>> routes = new ConcurrentHashMap<>(); // 路由映射

    /**
     * 构造函数
     *
     * @param id                   网关ID
     * @param mqttClient           MQTT客户端实例
     * @param registry             设备注册表
     * @param protocol             协议支持的Mono对象
     * @param sessionManager       设备会话管理器
     * @param clientMessageHandler 客户端消息处理器
     */
    public MqttClientDeviceGateway(String id,
                                   MqttClient mqttClient,
                                   DeviceRegistry registry,
                                   Mono<ProtocolSupport> protocol,
                                   DeviceSessionManager sessionManager,
                                   DecodedClientMessageHandler clientMessageHandler) {
        super(id);
        this.mqttClient = Objects.requireNonNull(mqttClient, "mqttClient");
        this.registry = Objects.requireNonNull(registry, "registry");
        setProtocol(protocol);
        this.helper = new DeviceGatewayHelper(registry, sessionManager, clientMessageHandler);
    }
    /**
     * 获取协议支持的Mono对象
     *
     * @return 协议支持的Mono对象
     */
    protected Mono<ProtocolSupport> getProtocol() {
        return protocol;
    }
    /**
     * 设置协议支持的Mono对象。此方法用于设定当前通信协议所支持的Mono对象。Mono对象是一种响应式编程中的表示异步数据流的方式，
     * 这里它被用于表示协议支持的消息编解码器。
     *
     * @param protocol 协议支持的Mono对象。该参数不应为null，它是一个Mono<ProtocolSupport>类型，表示一个异步获取的协议支持实例。
     *                 通过这个Mono对象，可以进一步获取协议相关的消息编解码器。
     */
    public void setProtocol(Mono<ProtocolSupport> protocol) {
        // 确保传入的protocol不为null，否则抛出异常
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        // 根据当前传输协议获取相应的消息编解码器，这是一个异步操作
        this.codecMono = protocol.flatMap(p -> p.getMessageCodec(getTransport()));
    }


    /**
     * 重新加载路由信息的函数。
     * 此函数首先获取当前协议支持的路由信息，然后筛选出Mqtt类型的路由，收集这些路由信息后，
     * 如果收集到的路由为空，则记录警告日志。最后，执行路由信息的重新加载操作。
     *
     * @return 返回一个Mono<Void>，表示异步操作完成，不返回任何结果。
     */
    protected Mono<Void> reload() {

        return this
            .getProtocol() // 获取当前协议
            .flatMap(support -> support
                .getRoutes(DefaultTransport.MQTT) // 获取MQTT传输协议下的路由信息
                .filter(MqttRoute.class::isInstance) // 筛选出Mqtt类型的路由
                .cast(MqttRoute.class) // 类型转换
                .collectList() // 收集筛选后的路由信息至List中
                .doOnEach(ReactiveLogger
                              .onNext(routes -> {
                                  // 如果协议包里没有配置Mqtt Topic信息，则记录警告日志
                                  if (CollectionUtils.isEmpty(routes)) {
                                      log.warn("The protocol [{}] is not configured with topics information", support.getId());
                                  }
                              }))
                .doOnNext(this::doReloadRoute)) // 执行路由信息的重新加载操作
            .then(); // 表示异步操作完成
    }


    /**
     * 重新加载路由信息，更新MQTT订阅。
     * 遍历提供的路由列表，对上行话题进行订阅更新，同时取消订阅不再需要的话题。
     *
     * @param routes 要加载的路由列表，包含MQTT话题和QoS等级。
     */
    protected void doReloadRoute(List<MqttRoute> routes) {
        // 创建一个映射，用于存储需要被移除的订阅
        Map<RouteKey, Tuple2<Integer, Disposable>> readyToRemove = new HashMap<>(this.routes);

        for (MqttRoute route : routes) {
            // 如果不是上行话题，则跳过此次循环，不进行订阅
            if (!route.isUpstream()) {
                continue;
            }
            // 将路由话题转换为MQTT格式
            String topic = convertToMqttTopic(route.getTopic());
            // 创建路由键
            RouteKey key = RouteKey.of(topic, route.getQos());
            // 从待移除映射中移除当前路由，表示此订阅需要更新或保留
            readyToRemove.remove(key);
            // 尝试更新或新增订阅
            this.routes.compute(key, (_key, old) -> {
                if (old != null) {
                    // 如果QoS没有变化，则保留旧的订阅，不进行重复订阅
                    if (old.getT1().equals(_key.qos)) {
                        return old;
                    } else {
                        // 如果QoS变化，取消旧的订阅
                        old.getT2().dispose();
                    }
                }
                // 进行新的订阅，并记录到订阅映射中
                return Tuples.of(_key.qos, doSubscribe(_key.topic, _key.qos));
            });
        }
        // 对于剩余在待移除映射中的订阅，表示这些订阅不再需要，进行取消订阅操作
        for (Map.Entry<RouteKey, Tuple2<Integer, Disposable>> value : readyToRemove.entrySet()) {
            this.routes.remove(value.getKey());
            // 取消订阅
            value.getValue().getT2().dispose();
        }
    }


    /**
     * 将给定的主题转换为MQTT主题格式。
     *
     * @param topic 需要转换的原始主题字符串。
     * @return 转换后的MQTT主题字符串。
     */
    protected static String convertToMqttTopic(String topic) {
        // 使用TopicUtils工具类的convertToMqttTopic方法进行主题转换
        return TopicUtils.convertToMqttTopic(topic);
    }

    /**
     * 获取传输协议类型。
     * 这个方法不接受任何参数，它用于返回系统默认的传输协议，即MQTT协议。
     *
     * @return Transport 返回传输协议的枚举类型。这里固定返回MQTT协议。
     */
    public Transport getTransport() {
        return DefaultTransport.MQTT;
    }


    /**
     * 订阅给定主题的MQTT消息。
     *
     * @param topic 要订阅的主题。
     * @param qos 质量等级（Quality of Service）。
     * @return Disposable对象，用于取消订阅。
     */
    protected Disposable doSubscribe(String topic, int qos) {
        // 使用MQTT客户端订阅指定主题，然后过滤掉在订阅过程中客户端未启动的消息。
        return mqttClient
            .subscribe(Collections.singletonList(topic), qos)
            .filter(msg -> isStarted()) // 确保只有在客户端启动时才处理消息。
            .flatMap(mqttMessage -> codecMono // 使用编解码器处理接收到的MQTT消息。
                .flatMapMany(codec -> codec
                    .decode(FromDeviceMessageContext.of(
                        new UnknownDeviceMqttClientSession(getId(), mqttClient, monitor),
                        mqttMessage,
                        registry,
                        msg -> handleMessage(mqttMessage, msg).then())))
                .cast(DeviceMessage.class) // 将解码后的消息转换为DeviceMessage类型。
                .concatMap(message -> handleMessage(mqttMessage, message)) // 处理设备消息。
                .subscribeOn(Schedulers.parallel()) // 在并行线程上处理消息。
                .onErrorResume((err) -> {
                    log.error("handle mqtt client message error:{}", mqttMessage, err); // 记录处理消息时的错误。
                    return Mono.empty(); // 发生错误时，返回空Mono对象以继续流程。
                }), Integer.MAX_VALUE)
            .contextWrite(ReactiveLogger.start("gatewayId", getId())) // 添加日志上下文。
            .subscribe(); // 订阅处理流并返回Disposable对象。
    }


    /**
     * 处理接收到的MQTT消息。
     *
     * @param mqttMessage 接收到的MQTT消息对象，包含消息内容和相关属性。
     * @param message 解析后的设备消息，准备进行进一步处理。
     * @return 返回一个Mono<Void>，表示异步处理操作完成。当处理完成后，无论成功与否，都不返回具体结果。
     */
    private Mono<Void> handleMessage(MqttMessage mqttMessage, DeviceMessage message) {
        // 监控收到的消息
        monitor.receivedMessage();
        // 处理设备消息，根据设备状态执行相应的操作
        return helper
            .handleDeviceMessage(message,
                                 device -> createDeviceSession(device, mqttClient), // 成功获取设备信息时的处理逻辑
                                 ignore -> {}, // 获取设备信息失败时的处理逻辑，此处选择忽略
                                 () -> log.warn("can not get device info from message:{},{}", mqttMessage.print(), message) // 获取设备信息异常时的处理逻辑
            )
            .then(); // 确保所有操作完成后，返回空Mono
    }


    @AllArgsConstructor(staticName = "of")
    @EqualsAndHashCode(exclude = "qos")
    private static class RouteKey {
        private String topic;
        private int qos;
    }

    /**
     * 创建一个设备会话。
     *
     * @param device 表示设备的操作员，用于设备的操作和管理。
     * @param client 表示MQTT客户端，用于设备与服务器之间的通信。
     * @return 返回一个设备会话实例，该实例封装了设备标识、设备操作、MQTT客户端以及监控信息。
     */
    private MqttClientSession createDeviceSession(DeviceOperator device, MqttClient client) {
        // 创建并返回一个新的设备会话实例
        return new MqttClientSession(device.getDeviceId(), device, client, monitor);
    }


    /**
     * 执行关闭操作，释放所有资源。
     * 遍历并取消所有路线相关的Disposable资源，然后清空路线集合。
     * @return 返回一个空的Mono<Void>，表示异步关闭操作完成。
     */
    @Override
    protected Mono<Void> doShutdown() {
        // 遍历并取消所有订阅的Disposable资源
        for (Tuple2<Integer, Disposable> value : routes.values()) {
            value.getT2().dispose();
        }
        // 清空路线集合，释放所有资源
        routes.clear();
        // 返回一个空的Mono，表示关闭操作已完成
        return Mono.empty();
    }


    /**
     * 在组件启动时执行的重载方法。
     * 这个方法的主要作用是调用 {@code reload()} 方法来完成启动时的重载逻辑。
     *
     * @return {@link Mono<Void>} 表示异步操作完成的结果，没有具体的返回值。
     */
    @Override
    protected Mono<Void> doStartup() {
        return reload(); // 执行启动时的重载逻辑
    }
}
