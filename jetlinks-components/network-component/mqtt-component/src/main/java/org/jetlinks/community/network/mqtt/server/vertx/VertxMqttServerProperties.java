package org.jetlinks.community.network.mqtt.server.vertx;

import lombok.*;
import org.jetlinks.community.network.AbstractServerNetworkConfig;
import org.jetlinks.community.network.resource.NetworkTransport;

/**
 * VertxMqttServerProperties 类用于配置 MQTT 服务器的属性，继承自 AbstractServerNetworkConfig。
 * 通过 Lombok 提供的注解简化了对象的创建和访问。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VertxMqttServerProperties extends AbstractServerNetworkConfig {

    // 服务实例数量(线程数)默认为当前运行的处理器数量。
    private int instance = Runtime.getRuntime().availableProcessors();

    // 最大消息长度默认为 8096 字节。
    private int maxMessageSize = 8096;

    /**
     * 获取网络传输类型。
     * @return 返回该服务器使用的网络传输类型，这里固定为 TCP。
     */
    @Override
    public NetworkTransport getTransport() {
        return NetworkTransport.TCP;
    }

    /**
     * 获取协议 schema。
     * @return 返回该服务器使用的协议类型，这里为 "mqtt"。
     */
    @Override
    public String getSchema() {
        return "mqtt";
    }
}

