package org.jetlinks.community.network.mqtt.gateway.device;

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
import org.jetlinks.community.network.mqtt.client.MqttClient;
import org.jetlinks.supports.server.DecodedClientMessageHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
/**
 * MqttClientDeviceGatewayProvider类实现了DeviceGatewayProvider接口，
 * 用于提供MQTT客户端设备网关的功能。
 */
public class MqttClientDeviceGatewayProvider implements DeviceGatewayProvider {
    private final NetworkManager networkManager; // 网络管理器
    private final DeviceRegistry registry; // 设备注册表
    private final DeviceSessionManager sessionManager; // 会话管理器
    private final DecodedClientMessageHandler clientMessageHandler; // 客户端消息处理器
    private final ProtocolSupports protocolSupports; // 协议支持

    /**
     * 构造函数，初始化MQTT客户端设备网关提供者。
     *
     * @param networkManager 网络管理器，用于管理网络连接。
     * @param registry 设备注册表，用于注册和管理设备。
     * @param sessionManager 会话管理器，用于管理设备会话。
     * @param clientMessageHandler 客户端消息处理器，用于处理客户端消息。
     * @param protocolSupports 协议支持，用于支持多种协议。
     */
    public MqttClientDeviceGatewayProvider(NetworkManager networkManager,
                                           DeviceRegistry registry,
                                           DeviceSessionManager sessionManager,
                                           DecodedClientMessageHandler clientMessageHandler,
                                           ProtocolSupports protocolSupports) {
        this.networkManager = networkManager;
        this.registry = registry;
        this.sessionManager = sessionManager;
        this.clientMessageHandler = clientMessageHandler;
        this.protocolSupports = protocolSupports;
    }

    @Override
    /**
     * 获取设备网关提供者的唯一标识符。
     *
     * @return 返回字符串类型的唯一标识符。
     */
    public String getId() {
        return "mqtt-client-gateway";
    }

    @Override
    /**
     * 获取设备网关提供者的名字。
     *
     * @return 返回设备网关提供者的名称字符串。
     */
    public String getName() {
        return "MQTT Broker接入";
    }

    /**
     * 获取网络类型。
     *
     * @return 返回默认的MQTT客户端网络类型。
     */
    public NetworkType getNetworkType() {
        return DefaultNetworkType.MQTT_CLIENT;
    }

    /**
     * 获取传输协议。
     *
     * @return 返回默认的MQTT传输协议。
     */
    public Transport getTransport() {
        return DefaultTransport.MQTT;
    }

    @Override
    /**
     * 基于给定的属性创建一个新的设备网关实例。
     *
     * @param properties 设备网关属性，包含设备网关的配置信息，如通道ID、协议等。
     * @return 返回一个Mono<DeviceGateway>，表示异步创建的设备网关操作。Mono是一个响应式流的类型，代表一个单个值，这个值可能是立即可用的，也可能是延迟计算的。
     */
    public Mono<DeviceGateway> createDeviceGateway(DeviceGatewayProperties properties) {
        // 根据属性获取网络组件并创建MQTT客户端设备网关
        return networkManager
            .<MqttClient>getNetwork(getNetworkType(), properties.getChannelId())
            .map(mqttClient -> {
                String protocol = properties.getProtocol();
                // 创建MQTT客户端设备网关实例，配置设备ID、MQTT客户端、协议支持等
                return new MqttClientDeviceGateway(properties.getId(),
                                                   mqttClient,
                                                   registry,
                                                   Mono.defer(() -> protocolSupports.getProtocol(protocol)),
                                                   sessionManager,
                                                   clientMessageHandler
                );
            });
    }


    @Override
    /**
     * 重新加载设备网关，基于新的属性进行配置更新。
     * 这个方法首先会检查设备网关的网络组件是否发生变化，
     * 如果有变化，则会关闭当前网关，创建一个新的网关并启动它。
     * 如果网络组件没有变化，则仅更新协议包并重新加载设备网关。
     *
     * @param gateway 当前设备网关实例，需要被重新加载或更新的网关。
     * @param properties 新的设备网关属性，包含需要应用到网关的配置信息。
     * @return 返回一个Mono<? extends DeviceGateway>，表示异步重新加载设备网关的操作。
     *         如果网络组件发生变化，返回新的设备网关实例；
     *         如果未发生变化，返回更新后的当前设备网关实例。
     */
    public Mono<? extends DeviceGateway> reloadDeviceGateway(DeviceGateway gateway, DeviceGatewayProperties properties) {
        MqttClientDeviceGateway deviceGateway = ((MqttClientDeviceGateway) gateway);
        String networkId = properties.getChannelId();

        // 检查网络组件是否发生变化
        if (!Objects.equals(networkId, deviceGateway.mqttClient.getId())) {
            // 如果网络组件发生变化，关闭当前网关，然后创建并启动新网关
            return gateway
                .shutdown() // 关闭当前网关
                .then(this
                          .createDeviceGateway(properties) // 创建新网关
                          .flatMap(gate -> gate.startup().thenReturn(gate)) // 启动新网关并返回
                );
        }
        // 如果网络组件未变化，仅更新协议包并重新加载设备网关
        deviceGateway.setProtocol(protocolSupports.getProtocol(properties.getProtocol()));
        return deviceGateway
            .reload() // 重新加载设备网关
            .thenReturn(deviceGateway); // 返回更新后的设备网关
    }

}

