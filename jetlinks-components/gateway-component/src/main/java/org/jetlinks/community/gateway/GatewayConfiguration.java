package org.jetlinks.community.gateway;

import org.jetlinks.community.gateway.supports.*;
import org.jetlinks.community.network.channel.ChannelProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * 自动配置类，当类路径上存在ChannelProvider类时，该配置类生效。
 * 主要负责设备网关的配置与管理。
 */
@AutoConfiguration
@ConditionalOnClass(ChannelProvider.class)
public class GatewayConfiguration {

    /**
     * 根据条件创建设备网关管理器Bean。
     * 当设备网关属性管理器DeviceGatewayPropertiesManager存在时，该Bean生效。
     *
     * @param channelProviders 通道提供者对象提供者，用于获取通道提供者实例。
     * @param gatewayProviders 设备网关提供者对象提供者，用于获取设备网关提供者实例。
     * @param propertiesManager 设备网关属性管理器，用于管理设备网关的属性配置。
     * @return 返回配置好的设备网关管理器实例。
     */
    @Bean
    @ConditionalOnBean(DeviceGatewayPropertiesManager.class)
    public DeviceGatewayManager deviceGatewayManager(ObjectProvider<ChannelProvider> channelProviders,
                                                     ObjectProvider<DeviceGatewayProvider> gatewayProviders,
                                                     DeviceGatewayPropertiesManager propertiesManager) {
        // 创建默认设备网关管理器并初始化
        DefaultDeviceGatewayManager gatewayManager=new DefaultDeviceGatewayManager(propertiesManager);
        // 添加通道提供者和设备网关提供者到管理器
        channelProviders.forEach(gatewayManager::addChannelProvider);
        gatewayProviders.forEach(gatewayManager::addGatewayProvider);
        // 注册设备网关提供者
        gatewayProviders.forEach(DeviceGatewayProviders::register);
        return gatewayManager;
    }

    /**
     * 创建并返回子设备网关提供者Bean。
     *
     * @return 返回一个子设备网关提供者实例。
     */
    @Bean
    public ChildDeviceGatewayProvider childDeviceGatewayProvider(){
        return new ChildDeviceGatewayProvider();
    }

}

