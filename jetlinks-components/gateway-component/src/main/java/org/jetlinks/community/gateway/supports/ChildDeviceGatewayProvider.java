package org.jetlinks.community.gateway.supports;

import org.jetlinks.core.message.codec.Transport;
import org.jetlinks.community.gateway.AbstractDeviceGateway;
import org.jetlinks.community.gateway.DeviceGateway;
import org.jetlinks.community.network.NetworkType;
import reactor.core.publisher.Mono;

/**
 * 提供者类实现，用于网关子设备的接入。
 */
public class ChildDeviceGatewayProvider implements DeviceGatewayProvider {

    /**
     * 获取提供者的唯一标识符。
     *
     * @return 提供者的ID字符串。
     */
    @Override
    public String getId() {
        return "child-device";
    }

    /**
     * 获取提供者的名称。
     *
     * @return 提供者的名称字符串。
     */
    @Override
    public String getName() {
        return "网关子设备接入";
    }

    /**
     * 获取提供者的描述信息。
     *
     * @return 提供者的描述字符串。
     */
    @Override
    public String getDescription() {
        return "通过其他网关设备来接入子设备";
    }

    /**
     * 获取提供者使用的通道标识符。
     *
     * @return 通道标识符字符串。
     */
    @Override
    public String getChannel() {
        return "child-device";
    }

    /**
     * 获取传输类型信息。
     *
     * @return 代表传输方式的Transport对象。
     */
    public Transport getTransport() {
        return Transport.of("Gateway");
    }

    /**
     * 基于给定的设备网关属性创建设备网关实例。
     *
     * @param properties 设备网关的属性，包含必要的配置信息。
     * @return 返回一个Mono对象，包含创建的设备网关实例。
     */
    @Override
    public Mono<? extends DeviceGateway> createDeviceGateway(DeviceGatewayProperties properties) {
        return Mono.just(new ChildDeviceGateway(properties.getId()));
    }

    /**
     * 子设备网关类，继承自AbstractDeviceGateway，实现设备的启动和关闭操作。
     */
    static class ChildDeviceGateway extends AbstractDeviceGateway {

        /**
         * 子设备网关的构造函数。
         *
         * @param id 设备的唯一标识符。
         */
        public ChildDeviceGateway(String id) {
            super(id);
        }

        /**
         * 执行设备的关闭操作。
         *
         * @return 返回一个Mono对象，在设备关闭操作完成后被订阅。
         */
        @Override
        protected Mono<Void> doShutdown() {
            return Mono.empty();
        }

        /**
         * 执行设备的启动操作。
         *
         * @return 返回一个Mono对象，在设备启动操作完成后被订阅。
         */
        @Override
        protected Mono<Void> doStartup() {
            return Mono.empty();
        }
    }
}

