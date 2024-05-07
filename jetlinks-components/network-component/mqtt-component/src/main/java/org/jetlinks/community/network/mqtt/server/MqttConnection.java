package org.jetlinks.community.network.mqtt.server;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.core.server.ClientConnection;
import org.jetlinks.core.server.mqtt.MqttAuth;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * MQTT连接信息,一个MQTT连接就是一个MQTT客户端.
 * <pre>
 *     //先定义处理逻辑
 *     connection
 *        .handleMessage()
 *        .subscribe(msg-> log.info("handle mqtt message:{}",msg));
 *
 *     //再接受连接
 *     connection.accept();
 *
 * </pre>
 *
 * @author zhouhao
 * @version 1.0
 * @since 1.0
 */
public interface MqttConnection extends ClientConnection {

    /**
     * 获取MQTT客户端ID
     *
     * @return clientId 返回MQTT客户端的唯一标识符
     */
    String getClientId();

    /**
     * 获取MQTT认证信息
     *
     * @return 可选的MQTT认证信息，如果不存在认证信息则返回空
     */
    Optional<MqttAuth> getAuth();

    /**
     * 拒绝MQTT连接
     *
     * @param code 返回码，用于指示连接被拒绝的原因
     * @see MqttConnectReturnCode 用于参考MQTT连接返回码的枚举
     */
    void reject(MqttConnectReturnCode code);

    /**
     * 接受连接.接受连接后才能进行消息收发.
     *
     * @return 当前连接信息，提供了进一步操作连接的方法
     */
    MqttConnection accept();

    /**
     * 获取遗言消息
     *
     * @return 可选的遗言信息，如果客户端设置了遗言，此方法将返回遗言消息
     */
    Optional<MqttMessage> getWillMessage();

    /**
     * 订阅客户端推送的消息
     *
     * @return 消息流，用于接收客户端发布的消息
     */
    Flux<MqttPublishing> handleMessage();

    /**
     * 推送消息到客户端
     *
     * @param message MQTT消息，包含消息的主题和内容
     * @return 异步推送结果，当消息成功推送时完成
     */
    Mono<Void> publish(MqttMessage message);

    /**
     * 订阅客户端订阅请求
     *
     * @param autoAck 是否自动应答订阅请求
     * @return 订阅请求流，包含客户端订阅的主题等信息
     */
    Flux<MqttSubscription> handleSubscribe(boolean autoAck);

    /**
     * 订阅客户端取消订阅请求
     *
     * @param autoAck 是否自动应答取消订阅请求
     * @return 取消订阅请求流，包含客户端希望取消订阅的主题等信息
     */
    Flux<MqttUnSubscription> handleUnSubscribe(boolean autoAck);

    /**
     * 监听断开连接
     *
     * @param listener 监听器，当连接断开时将被调用
     */
    void onClose(Consumer<MqttConnection> listener);

    /**
     * 获取MQTT连接是否存活,当客户端断开连接或者 客户端ping超时后则返回false.
     *
     * @return mqtt连接是否存活的布尔值
     */
    boolean isAlive();

    /**
     * 关闭mqtt连接
     *
     * @return 异步关闭结果，当连接成功关闭时完成
     */
    Mono<Void> close();

    /**
     * 获取最后一次ping的时间
     *
     * @return 最后一次ping的时间戳
     */
    long getLastPingTime();

    /**
     * 保持连接活跃
     */
    void keepAlive();

    /**
     * 获取保持连接活跃的超时时间
     *
     * @return 保持连接活跃的超时时间
     */
    Duration getKeepAliveTimeout();

    /**
     * 设置保持连接活跃的超时时间
     *
     * @param duration 指定保持连接活跃的超时时间
     */
    void setKeepAliveTimeout(Duration duration);

    /**
     * 获取客户端的地址
     *
     * @return 客户端的地址信息
     */
    InetSocketAddress getClientAddress();
}

