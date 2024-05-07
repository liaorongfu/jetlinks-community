package org.jetlinks.community.gateway.monitor;

/**
 * DeviceGatewayMonitorSupplier接口定义了获取设备网关监控器的方法。
 * 该接口用于通过设备的ID和标签来获取相应的设备网关监控器实例。
 */
public interface DeviceGatewayMonitorSupplier {
    /**
     * 根据设备ID和标签获取设备网关监控器。
     *
     * @param id 设备的唯一标识符。
     * @param tags 与设备相关的标签，可用于过滤或分类设备。
     * @return 根据提供的ID和标签返回的DeviceGatewayMonitor实例。
     */
    DeviceGatewayMonitor getDeviceGatewayMonitor(String id, String... tags);
}

