package org.jetlinks.community.network.manager.service;

import org.jetlinks.community.gateway.supports.DeviceGatewayProperties;
import org.jetlinks.community.gateway.supports.DeviceGatewayPropertiesManager;
import org.jetlinks.community.gateway.supports.DeviceGatewayProvider;
import org.jetlinks.community.network.manager.entity.DeviceGatewayEntity;
import org.jetlinks.community.reference.DataReferenceInfo;
import org.jetlinks.community.reference.DataReferenceManager;
import org.jetlinks.community.reference.DataReferenceProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 设备网关配置服务类，实现了DeviceGatewayPropertiesManager和DataReferenceProvider接口。
 * 用于管理设备网关的配置和数据引用。
 */
@Service
public class DeviceGatewayConfigService implements DeviceGatewayPropertiesManager, DataReferenceProvider {

    // 设备网关服务，用于进行设备网关的数据库操作
    private final DeviceGatewayService deviceGatewayService;

    /**
     * 获取数据引用提供者的类型ID。
     *
     * @return 返回网络类型的数据引用ID。
     */
    @Override
    public String getId() {
        return DataReferenceManager.TYPE_NETWORK;
    }

    /**
     * 根据网络ID获取数据引用信息。
     *
     * @param networkId 网络的唯一标识符。
     * @return 返回一个包含数据引用信息的Flux流。
     */
    @Override
    public Flux<DataReferenceInfo> getReference(String networkId) {
        // 查询指定网络ID的设备网关信息，并转换为数据引用信息
        return deviceGatewayService
            .createQuery()
            .where()
            .and(DeviceGatewayEntity::getChannel, DeviceGatewayProvider.CHANNEL_NETWORK)
            .is(DeviceGatewayEntity::getChannelId, networkId)
            .fetch()
            .map(e -> DataReferenceInfo.of(e.getId(), DataReferenceManager.TYPE_NETWORK, e.getChannelId(), e.getName()));
    }

    /**
     * 获取所有数据引用信息。
     *
     * @return 返回一个包含所有数据引用信息的Flux流。
     */
    @Override
    public Flux<DataReferenceInfo> getReferences() {
        // 查询所有网络通道的设备网关信息，并转换为数据引用信息
        return deviceGatewayService
            .createQuery()
            .where()
            .and(DeviceGatewayEntity::getChannel, DeviceGatewayProvider.CHANNEL_NETWORK)
            .notNull(DeviceGatewayEntity::getChannelId)
            .fetch()
            .map(e -> DataReferenceInfo.of(e.getId(), DataReferenceManager.TYPE_NETWORK, e.getChannelId(), e.getName()));
    }

    /**
     * 构造函数，初始化设备网关配置服务。
     *
     * @param deviceGatewayService 设备网关服务实例。
     */
    public DeviceGatewayConfigService(DeviceGatewayService deviceGatewayService) {
        this.deviceGatewayService = deviceGatewayService;
    }

    /**
     * 根据设备网关ID获取其配置属性。
     *
     * @param id 设备网关的唯一标识符。
     * @return 返回一个包含设备网关配置的Mono流。
     */
    @Override
    public Mono<DeviceGatewayProperties> getProperties(String id) {
        // 根据ID查询设备网关信息，并转换为配置属性
        return deviceGatewayService
            .findById(id)
            .map(DeviceGatewayEntity::toProperties);
    }

    /**
     * 根据通道类型获取所有设备网关的配置属性。
     *
     * @param channel 设备网关的通道类型。
     * @return 返回一个包含所有指定通道类型设备网关配置属性的Flux流。
     */
    @Override
    public Flux<DeviceGatewayProperties> getPropertiesByChannel(String channel) {
        // 根据通道类型查询所有设备网关信息，并转换为配置属性
        return deviceGatewayService
            .createQuery()
            .where()
            .and(DeviceGatewayEntity::getChannel, channel)
            .fetch()
            .map(DeviceGatewayEntity::toProperties);
    }

}

