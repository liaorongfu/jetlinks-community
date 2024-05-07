package org.jetlinks.community.network.mqtt.server.vertx;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jctools.maps.NonBlockingHashMap;
import org.jetlinks.community.network.mqtt.server.MqttConnection;
import org.jetlinks.community.network.mqtt.server.MqttServer;
import org.jetlinks.core.utils.Reactors;
import org.jetlinks.community.network.DefaultNetworkType;
import org.jetlinks.community.network.NetworkType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于Vert.x实现的Mqtt服务器类，提供了Mqtt服务器的基本功能和扩展。
 */
@Slf4j
public class VertxMqttServer implements MqttServer {

    // 用于存储连接的Sink，限制最大连接数为5*1024
    private final Sinks.Many<MqttConnection> sink = Reactors.createMany(5 * 1024, false);

    // 用于存储主题对应的连接Sink列表
    private final Map<String, List<Sinks.Many<MqttConnection>>> sinks =
        new NonBlockingHashMap<>();

    // Vert.x Mqtt服务器实例集合
    private Collection<io.vertx.mqtt.MqttServer> mqttServer;

    // 服务器唯一标识
    private final String id;

    // 最后一次错误信息
    @Getter
    @Setter
    private String lastError;

    // 绑定地址
    @Setter(AccessLevel.PACKAGE)
    private InetSocketAddress bind;

    /**
     * 构造函数，初始化Mqtt服务器。
     *
     * @param id 服务器唯一标识。
     */
    public VertxMqttServer(String id) {
        this.id = id;
    }

    /**
     * 设置Mqtt服务器实例集合。
     * 该方法用于替换当前的Mqtt服务器实例集合，并对每个服务器实例设置异常处理器和端点处理器。
     * 如果已存在实例且不为空，则先关闭当前实例。
     *
     * @param mqttServer Mqtt服务器实例集合。这是一个io.vertx.mqtt.MqttServer类型的集合，用于存储Mqtt服务器实例。
     */
    public void setMqttServer(Collection<io.vertx.mqtt.MqttServer> mqttServer) {
        // 如果已存在实例且不为空，则先关闭当前实例
        if (this.mqttServer != null && !this.mqttServer.isEmpty()) {
            shutdown();
        }
        this.mqttServer = mqttServer;
        // 遍历设置异常处理器和端点处理器
        for (io.vertx.mqtt.MqttServer server : this.mqttServer) {
            // 为每个服务器实例设置异常处理器，记录错误信息
            server
                .exceptionHandler(error -> {
                    log.error(error.getMessage(), error);
                })
                // 为每个服务器实例设置端点处理器，处理Mqtt连接
                .endpointHandler(endpoint -> {
                    handleConnection(new VertxMqttConnection(endpoint));
                });
        }
    }


    /**
     * 尝试将连接发给下一个订阅者。
     * 这个方法尝试将一个MQTT连接实例发给一个订阅了特定Sink的下一个订阅者。
     * 如果此时没有订阅者，那么连接不会被发出，方法将返回false。
     *
     * @param sink 接收连接的Sink。Sink是一种多订阅者数据流，用于接收和分发MQTT连接。
     * @param connection Mqtt连接实例。这是要发给下一个订阅者的MQTT连接对象。
     * @return 如果成功发出连接则返回true，否则返回false。如果成功将连接发给下一个订阅者，返回true；如果没有订阅者或发出过程中出错，则返回false。
     */
    private boolean emitNext(Sinks.Many<MqttConnection> sink, VertxMqttConnection connection){
        // 检查是否有订阅者。如果没有，则不尝试发出连接。
        if (sink.currentSubscriberCount() <= 0) {
            return false;
        }
        try{
            // 尝试将连接发给下一个订阅者，并处理可能发生的异常。
            sink.emitNext(connection,Reactors.emitFailureHandler());
            return true;
        }catch (Throwable ignore){}
        // 如果发出过程中捕获到异常，静默处理异常并返回false。
        return false;
    }


    /**
     * 处理新连接，尝试将其发给所有订阅者或拒绝连接。
     * 这个方法首先尝试将新连接发送给全局sink，如果成功则不需要进一步处理。
     * 如果全局sink处理失败，则尝试将连接发送给所有主题sink，至少一个sink处理成功即认为连接被处理。
     * 如果没有任何sink处理连接，则拒绝连接。
     *
     * @param connection Mqtt连接实例，代表一个新建立的Mqtt连接。
     */
    private void handleConnection(VertxMqttConnection connection) {
        // 尝试向全局sink发送连接
        boolean anyHandled = emitNext(sink, connection);

        // 遍历所有主题sink，尝试发送连接，至少一个sink处理成功则认为连接被处理
        for (List<Sinks.Many<MqttConnection>> value : sinks.values()) {
            // 如果主题sink列表为空，则跳过当前主题
            if (value.size() == 0) {
                continue;
            }
            // 随机选择一个sink尝试发送连接
            Sinks.Many<MqttConnection> sink = value.get(ThreadLocalRandom.current().nextInt(value.size()));
            // 如果发送成功，则标记为已处理
            if (emitNext(sink, connection)) {
                anyHandled = true;
            }
        }
        // 如果没有任何sink处理连接，则拒绝连接
        if (!anyHandled) {
            connection.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
        }
    }


    /**
     * 提供一个Flux流来处理所有连接。
     * 这个方法重写了抽象方法handleConnection，用于返回一个Flux流，该流包含了所有的Mqtt连接。
     *
     * @return 返回一个Flux<MqttConnection>，它是一个响应式流，能够顺序地处理所有的Mqtt连接。
     */
    @Override
    public Flux<MqttConnection> handleConnection() {
        // 将内部使用的Sink转换为Flux流并返回，实现对所有连接的响应式处理。
        return sink.asFlux();
    }


    /**
     * 根据holder键来处理特定主题的连接。
     * 此方法会根据提供的holder键来为特定主题创建或获取一个连接的Flux流。
     * 如果对应主题的连接sink不存在，则会初始化一个新的连接sink列表。
     *
     * @param holder 主题键。用于标识特定的主题。
     * @return 返回一个Flux流，包含与特定主题相关的MqttConnection对象。
     *         订阅此Flux流的消费者可以在连接发生变化时得到通知。
     */
    @Override
    public Flux<MqttConnection> handleConnection(String holder) {
        // 获取或初始化主题的连接sink列表
        List<Sinks.Many<MqttConnection>> sinks = this
            .sinks
            .computeIfAbsent(holder, ignore -> new CopyOnWriteArrayList<>());

        // 创建一个新的连接sink并添加到主题列表中
        Sinks.Many<MqttConnection> sink = Reactors.createMany(Integer.MAX_VALUE,true);

        sinks.add(sink);

        // 返回新sink的Flux流，并在取消订阅时从列表中移除sink
        return sink
            .asFlux()
            .doOnCancel(() -> sinks.remove(sink));
    }


    /**
     * 检查服务器是否存活，即是否有正在运行的服务器实例。
     * 这个方法通过检查存储服务器实例的变量是否非空且不为空集合来判断服务器是否存活。
     *
     * @return 如果至少有一个服务器实例正在运行，则返回true，否则返回false。
     *         该返回值依赖于变量`mqttServer`的状态，它代表了服务器实例的集合。
     */
    @Override
    public boolean isAlive() {
        // 检查mqttServer是否被初始化且不为空，以判断服务器是否存活
        return mqttServer != null && !mqttServer.isEmpty();
    }


    /**
     * 获取服务器是否支持自动重载。
     * 该方法用于查询服务器是否具备自动重载的功能。在当前的实现中，此功能始终不被支持。
     *
     * @return 返回一个布尔值，表示服务器是否支持自动重载。在当前实现中，始终返回false。
     */
    @Override
    public boolean isAutoReload() {
        return false;
    }


    /**
     * 获取服务器的唯一标识。
     *
     * @return 返回服务器的唯一标识。
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * 获取服务器类型。
     *
     * @return 返回服务器类型枚举。
     */
    @Override
    public NetworkType getType() {
        return DefaultNetworkType.MQTT_SERVER;
    }

    /**
     * 关闭所有Mqtt服务器实例。
     * <p>
     * 该方法不接受任何参数，也不返回任何结果。它主要用于遍历并关闭所有已初始化的Mqtt服务器实例。
     * 在关闭每个服务器实例时，会检查关闭操作是否成功，并相应地记录日志信息。
     */
    @Override
    public void shutdown() {
        if (mqttServer != null) {
            // 遍历mqttServer列表，逐个关闭服务器实例
            for (io.vertx.mqtt.MqttServer server : mqttServer) {
                server.close(res -> {
                    // 根据关闭操作的结果记录日志
                    if (res.failed()) {
                        log.error(res.cause().getMessage(), res.cause());
                    } else {
                        log.debug("mqtt server [{}] closed", server.actualPort());
                    }
                });
            }
            // 清空mqttServer列表，避免重复关闭
            mqttServer.clear();
        }
    }


    /**
     * 获取服务器绑定地址。
     * 这个方法用于返回服务器在启动时绑定的地址和端口信息。
     *
     * @return 返回服务器绑定的SocketAddress。SocketAddress是一个抽象类，代表网络上一个 socket 地址。
     *         具体返回的是InetSocketAddress实例，它提供了更丰富的功能，允许指定主机名或IP地址以及端口号。
     */
    @Override
    public InetSocketAddress getBindAddress() {
        return bind; // 返回服务器绑定的地址
    }

}

