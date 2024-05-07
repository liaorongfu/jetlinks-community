package org.jetlinks.community.network.mqtt.gateway.device.session;

import lombok.Generated;
import lombok.Getter;
import org.jetlinks.community.gateway.monitor.DeviceGatewayMonitor;
import org.jetlinks.community.network.mqtt.server.MqttConnection;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.message.codec.EncodedMessage;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.core.message.codec.Transport;
import org.jetlinks.core.server.session.DeviceSession;
import org.jetlinks.core.server.session.ReplaceableDeviceSession;
import org.jetlinks.community.gateway.monitor.DeviceGatewayMonitor;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * MQTT连接会话类，实现了DeviceSession和ReplaceableDeviceSession接口。
 * 用于管理和维护设备的MQTT连接状态。
 */
public class MqttConnectionSession implements DeviceSession, ReplaceableDeviceSession {

    // 设备会话ID
    @Getter
    @Generated
    private final String id;

    // 设备操作者
    @Getter
    @Generated
    private final DeviceOperator operator;

    // 传输协议类型
    @Getter
    @Generated
    private final Transport transport;

    // MQTT连接实例
    @Getter
    @Generated
    private MqttConnection connection;

    // 设备网关监控器
    private final DeviceGatewayMonitor monitor;

    // 连接建立时间
    private final long connectTime = System.currentTimeMillis();

    /**
     * 构造函数，初始化MQTT连接会话。
     *
     * @param id            设备会话ID
     * @param operator      设备操作者
     * @param transport     传输协议类型
     * @param connection    MQTT连接实例
     * @param monitor       设备网关监控器
     */
    public MqttConnectionSession(String id,
                                 DeviceOperator operator,
                                 Transport transport,
                                 MqttConnection connection,
                                 DeviceGatewayMonitor monitor) {
        this.id = id;
        this.operator = operator;
        this.transport = transport;
        this.connection = connection;
        this.monitor = monitor;
    }

    // 获取设备ID
    @Override
    public String getDeviceId() {
        return id;
    }

    // 获取最后ping的时间
    @Override
    public long lastPingTime() {
        return connection.getLastPingTime();
    }

    // 获取连接建立时间
    @Override
    public long connectTime() {
        return connectTime;
    }

    /**
     * 发送消息到设备。
     *
     * @param encodedMessage 编码后的消息
     * @return 返回发送结果的Mono对象
     */
    @Override
    public Mono<Boolean> send(EncodedMessage encodedMessage) {
        return Mono.defer(() -> connection.publish(((MqttMessage) encodedMessage)))
                   .doOnSuccess(nil -> monitor.sentMessage())
                   .thenReturn(true);
    }

    // 关闭连接
    @Override
    public void close() {
        connection.close().subscribe();
    }

    // 发送ping请求，保持连接活性
    @Override
    public void ping() {
        connection.keepAlive();
    }

    // 设置保持连接的超时时间
    @Override
    public void setKeepAliveTimeout(Duration timeout) {
        connection.setKeepAliveTimeout(timeout);
    }

    // 检查连接是否存活
    @Override
    public boolean isAlive() {
        return connection.isAlive();
    }

    // 在连接关闭时执行指定操作
    @Override
    public void onClose(Runnable call) {
        connection.onClose(c -> call.run());
    }

    // 获取客户端地址
    @Override
    public Optional<InetSocketAddress> getClientAddress() {
        return Optional.ofNullable(connection.getClientAddress());
    }

    /**
     * 用新的设备会话替换当前会话。
     *
     * @param session 新的设备会话
     */
    @Override
    public void replaceWith(DeviceSession session) {
        if (session instanceof MqttConnectionSession) {
            MqttConnectionSession connectionSession = ((MqttConnectionSession) session);
            if (!this.connection.equals(connectionSession.connection)) {
                this.connection.close().subscribe();
            }
            this.connection = connectionSession.connection;
        }
    }

    // 检查当前会话是否与另一个会话有变化
    @Override
    public boolean isChanged(DeviceSession another) {
        if (another.isWrapFrom(MqttConnectionSession.class)) {
            return !this
                .connection
                .equals(another.unwrap(MqttConnectionSession.class).getConnection());
        }
        return true;
    }

    // 对象相等比较，主要比较连接实例
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MqttConnectionSession that = (MqttConnectionSession) o;
        return Objects.equals(connection, that.connection);
    }

    // 生成基于连接实例的哈希码
    @Override
    public int hashCode() {
        return Objects.hash(connection);
    }
}

