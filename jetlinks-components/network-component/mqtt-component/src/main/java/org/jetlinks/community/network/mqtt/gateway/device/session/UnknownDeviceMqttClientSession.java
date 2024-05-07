package org.jetlinks.community.network.mqtt.gateway.device.session;

import lombok.Getter;
import org.jetlinks.community.gateway.monitor.DeviceGatewayMonitor;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.message.codec.EncodedMessage;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.core.message.codec.Transport;
import org.jetlinks.core.server.session.DeviceSession;
import org.jetlinks.community.gateway.monitor.DeviceGatewayMonitor;
import org.jetlinks.community.network.mqtt.client.MqttClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 代表一个未知设备的MQTT客户端会话实现类。
 * 这个类实现了DeviceSession接口，提供了设备会话的基本管理功能，包括消息发送、连接状态管理等。
 */
public class UnknownDeviceMqttClientSession implements DeviceSession {
    // 设备会话的唯一标识符
    @Getter
    private final String id;

    // MQTT客户端，用于与设备进行消息通信
    private final MqttClient client;

    // 设备网关监控器，用于监控设备的连接状态和消息发送情况
    private final DeviceGatewayMonitor monitor;
    // 会话的保活超时时间
    private Duration keepAliveTimeout;

    /**
     * 构造函数，初始化一个未知设备的MQTT客户端会话。
     *
     * @param id 设备会话的唯一标识符。
     * @param client 与设备通信的MQTT客户端。
     * @param monitor 设备网关监控器，用于监控设备的连接状态和消息发送。
     */
    public UnknownDeviceMqttClientSession(String id,
                                          MqttClient client,
                                          DeviceGatewayMonitor monitor) {
        this.id = id;
        this.client = client;
        this.monitor = monitor;
    }

    // 以下是一系列接口方法的实现，用于提供设备会话的基本信息和操作。

    @Override
    public String getDeviceId() {
        return null;
    }

    @Override
    public DeviceOperator getOperator() {
        return null;
    }

    @Override
    public long lastPingTime() {
        return 0;
    }

    @Override
    public long connectTime() {
        return 0;
    }

    /**
     * 设置保活超时时间。
     *
     * @param keepAliveTimeout 会话的保活超时时间。
     */
    @Override
    public void setKeepAliveTimeout(Duration keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }

    /**
     * 获取保活超时时间。
     *
     * @return 会话的保活超时时间。
     */
    @Override
    public Duration getKeepAliveTimeout() {
        return keepAliveTimeout;
    }

    /**
     * 发送消息到设备。
     *
     * @param encodedMessage 要发送的编码消息。
     * @return 返回一个Mono<Boolean>，表示消息是否成功发送。
     */
    @Override
    public Mono<Boolean> send(EncodedMessage encodedMessage) {
        if (encodedMessage instanceof MqttMessage) {
            // 如果消息类型支持，则使用MQTT客户端发送消息，并监控消息发送情况
            return client
                .publish(((MqttMessage) encodedMessage))
                .doOnSuccess(ignore->monitor.sentMessage())
                .thenReturn(true);
        }
        // 对于不支持的消息类型，返回一个错误
        return Mono.error(new UnsupportedOperationException("unsupported message type:" + encodedMessage.getClass()));
    }

    /**
     * 获取传输协议类型。
     *
     * @return 返回传输协议类型，此处为MQTT。
     */
    @Override
    public Transport getTransport() {
        return DefaultTransport.MQTT;
    }

    // 下面的方法提供了一些控制和查询会话状态的操作

    @Override
    public void close() {

    }

    @Override
    public void ping() {

    }

    /**
     * 检查客户端会话是否存活。
     *
     * @return 返回客户端是否存活的布尔值。
     */
    @Override
    public boolean isAlive() {
        return client.isAlive();
    }

    @Override
    public void onClose(Runnable call) {

    }
}

