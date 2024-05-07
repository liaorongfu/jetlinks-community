package org.jetlinks.community.network.mqtt.gateway.device;

import org.jetlinks.community.network.mqtt.server.MqttServer;
import org.jetlinks.core.ProtocolSupports;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.device.session.DeviceSessionManager;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.message.codec.Transport;
import org.jetlinks.community.gateway.DeviceGateway;
import org.jetlinks.community.gateway.supports.DeviceGatewayProperties;
import org.jetlinks.community.gateway.supports.DeviceGatewayProvider;
import org.jetlinks.community.network.DefaultNetworkType;
import org.jetlinks.community.network.NetworkManager;
import org.jetlinks.community.network.NetworkType;
import org.jetlinks.supports.server.DecodedClientMessageHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
public class MqttServerDeviceGatewayProvider implements DeviceGatewayProvider {

    private final NetworkManager networkManager;

    private final DeviceRegistry registry;

    private final DeviceSessionManager sessionManager;

    private final DecodedClientMessageHandler messageHandler;

    private final ProtocolSupports protocolSupports;

    public MqttServerDeviceGatewayProvider(NetworkManager networkManager,
                                           DeviceRegistry registry,
                                           DeviceSessionManager sessionManager,
                                           DecodedClientMessageHandler messageHandler,
                                           ProtocolSupports protocolSupports) {
        this.networkManager = networkManager;
        this.registry = registry;
        this.sessionManager = sessionManager;
        this.messageHandler = messageHandler;
        this.protocolSupports = protocolSupports;
    }

    @Override
    public String getId() {
        return "mqtt-server-gateway";
    }

    @Override
    public String getName() {
        return "MQTT直连接入";
    }


    public NetworkType getNetworkType() {
        return DefaultNetworkType.MQTT_SERVER;
    }

    public Transport getTransport() {
        return DefaultTransport.MQTT;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 创建一个设备网关实例。
     *
     * @param properties 设备网关的属性，包含设备网关的配置信息，如通道ID等。
     * @return 返回一个Mono类型的DeviceGateway实例，表示异步创建操作的结果。
     */
    @Override
    public Mono<DeviceGateway> createDeviceGateway(DeviceGatewayProperties properties) {

        // 通过网络管理器获取指定类型的网络服务，然后映射为MqttServerDeviceGateway实例
        return networkManager
            .<MqttServer>getNetwork(getNetworkType(), properties.getChannelId())
            .map(mqttServer -> new MqttServerDeviceGateway(
                properties.getId(),
                registry,
                sessionManager,
                mqttServer,
                messageHandler,
                Mono.empty()
            ));
    }


    /**
     * 重新加载设备网关。
     * 如果给定的设备网关的网络组件ID与属性中的网络ID不匹配，则关闭当前网关实例，
     * 并基于给定属性创建并启动一个新的网关实例。如果匹配，则不进行任何操作，直接返回当前网关实例。
     *
     * @param gateway 当前的设备网关实例，需要进行重新加载操作。
     * @param properties 设备网关的属性，包含需要使用的配置信息，如网络ID。
     * @return 返回一个Mono对象，包含重新加载后的设备网关实例。如果重新加载导致网关实例更换，则返回新的实例；否则返回相同的实例。
     */
    @Override
    public Mono<? extends DeviceGateway> reloadDeviceGateway(DeviceGateway gateway,
                                                             DeviceGatewayProperties properties) {
        MqttServerDeviceGateway deviceGateway = ((MqttServerDeviceGateway) gateway);

        String networkId = properties.getChannelId();
        // 检查网络组件ID是否发生变化
        if (!Objects.equals(networkId, deviceGateway.getMqttServer().getId())) {
            // 如果网络ID发生变化，则关闭当前网关，创建并启动新网关
            return gateway
                .shutdown() // 关闭当前网关
                .then(this
                    .createDeviceGateway(properties) // 创建新网关
                    .flatMap(gate -> gate.startup().thenReturn(gate))); // 启动新网关并返回
        }
        // 如果网络ID未发生变化，直接返回当前网关
        return Mono.just(gateway);
    }

}
