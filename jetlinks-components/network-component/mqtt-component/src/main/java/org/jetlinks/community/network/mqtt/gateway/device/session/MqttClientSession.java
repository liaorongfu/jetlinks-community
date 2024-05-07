package org.jetlinks.community.network.mqtt.gateway.device.session;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.community.gateway.monitor.DeviceGatewayMonitor;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.message.codec.EncodedMessage;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.core.message.codec.Transport;
import org.jetlinks.core.server.session.DeviceSession;
import org.jetlinks.community.network.mqtt.client.MqttClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * MQTT Client设备会话
 * MQTT客户端会话类，实现了设备会话接口。
 * 用于管理和维护设备通过MQTT协议建立的会话状态。
 * @author zhouhao
 * @since 1.0
 */
public class MqttClientSession implements DeviceSession {
    // 会话ID
    @Getter
    private final String id;

    // 设备操作者
    @Getter
    private final DeviceOperator operator;

    // MQTT客户端实例
    @Getter
    @Setter
    private MqttClient client;

    // 连接时间戳
    private final long connectTime = System.currentTimeMillis();

    // 上一次PING操作的时间戳
    private long lastPingTime = System.currentTimeMillis();

    // 会话保持活动的超时时间，-1表示无超时
    private long keepAliveTimeout = -1;

    // 设备网关监控器
    private final DeviceGatewayMonitor monitor;

    /**
     * 构造函数，初始化MQTT客户端会话。
     *
     * @param id 会话ID
     * @param operator 设备操作者
     * @param client MQTT客户端实例
     * @param monitor 设备网关监控器
     */
    public MqttClientSession(String id,
                             DeviceOperator operator,
                             MqttClient client,
                             DeviceGatewayMonitor monitor) {
        this.id = id;
        this.operator = operator;
        this.client = client;
        this.monitor = monitor;
    }

    /**
     * 获取设备ID。
     *
     * @return 设备ID
     */
    @Override
    public String getDeviceId() {
        return operator.getDeviceId();
    }

    /**
     * 获取最后PING操作的时间。
     *
     * @return 最后PING操作的时间戳
     */
    @Override
    public long lastPingTime() {
        return lastPingTime;
    }

    /**
     * 获取连接时间。
     *
     * @return 连接时间戳
     */
    @Override
    public long connectTime() {
        return connectTime;
    }

    /**
     * 发送消息到设备。
     *
     * @param encodedMessage 要发送的编码消息
     * @return 发送结果的Mono对象，成功返回true，失败返回错误
     */
    @Override
    public Mono<Boolean> send(EncodedMessage encodedMessage) {
        if (encodedMessage instanceof MqttMessage) {
            monitor.sentMessage();
            return client
                .publish(((MqttMessage) encodedMessage))
                .thenReturn(true)
                ;
        }
        return Mono.error(new UnsupportedOperationException("unsupported message type:" + encodedMessage.getClass()));
    }

    /**
     * 获取传输协议类型。
     *
     * @return 传输协议类型
     */
    @Override
    public Transport getTransport() {
        return DefaultTransport.MQTT;
    }

    /**
     * 关闭会话。
     */
    @Override
    public void close() {

    }

    /**
     * 执行PING操作，更新最后PING时间。
     */
    @Override
    public void ping() {
        lastPingTime = System.currentTimeMillis();
    }

    /**
     * 检查会话是否活跃。
     *
     * @return 如果会话活跃返回true，否则返回false
     */
    @Override
    public boolean isAlive() {
        return client.isAlive() &&
            (keepAliveTimeout <= 0 || System.currentTimeMillis() - lastPingTime < keepAliveTimeout);
    }

    /**
     * 设置关闭时执行的回调。
     *
     * @param call 关闭时要执行的Runnable
     */
    @Override
    public void onClose(Runnable call) {

    }

    /**
     * 设置保持活跃的超时时间。
     *
     * @param timeout 保持活跃的超时时间
     */
    @Override
    public void setKeepAliveTimeout(Duration timeout) {
        this.keepAliveTimeout = timeout.toMillis();
    }

    /**
     * 生成会话的字符串表示。
     *
     * @return 会话的字符串表示
     */
    @Override
    public String toString() {
        return "MqttClientSession{" +
            "id=" + id + ",device=" + getDeviceId() +
            '}';
    }
}

