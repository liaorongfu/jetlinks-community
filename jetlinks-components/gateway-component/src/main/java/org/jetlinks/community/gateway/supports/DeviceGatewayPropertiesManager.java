package org.jetlinks.community.gateway.supports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 设备网关属性管理器接口，用于管理和获取设备网关的属性信息。
 *
 * @author zhouhao
 */
public interface DeviceGatewayPropertiesManager {

    /**
     * 根据网关ID获取网关的属性。
     *
     * @param id 网关的唯一标识符。
     * @return 返回一个Mono对象，包含指定ID的网关属性，如果找不到则返回空。
     */
    Mono<DeviceGatewayProperties> getProperties(String id);

    /**
     * 根据通道ID获取所有关联网关的属性。
     *
     * @param channel 通道的唯一标识符。
     * @return 返回一个Flux对象，包含所有通过指定通道标识符关联的网关属性。
     */
    Flux<DeviceGatewayProperties> getPropertiesByChannel(String channel);

}

