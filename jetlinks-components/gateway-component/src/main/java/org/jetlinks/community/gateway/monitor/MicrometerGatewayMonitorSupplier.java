package org.jetlinks.community.gateway.monitor;

import org.jetlinks.community.micrometer.MeterRegistryManager;
import org.springframework.stereotype.Component;

/**
 * 一个实现了DeviceGatewayMonitorSupplier接口的组件，用于提供Micrometer设备网关监控功能。
 */
@Component
public class MicrometerGatewayMonitorSupplier implements DeviceGatewayMonitorSupplier {

    // Micrometer的度量注册管理器
    private final MeterRegistryManager meterRegistryManager;

    /**
     * 构造函数：初始化MicrometerGatewayMonitorSupplier，并在实例化时注册此监控供应商。
     *
     * @param meterRegistryManager 用于管理和访问度量注册的MeterRegistryManager实例。
     */
    public MicrometerGatewayMonitorSupplier(MeterRegistryManager meterRegistryManager) {
        this.meterRegistryManager = meterRegistryManager;
        GatewayMonitors.register(this); // 注册网关监控供应商
    }

    /**
     * 根据提供的ID和标签获取设备网关监控实例。
     *
     * @param id 设备网关的唯一标识符。
     * @param tags 与设备网关监控相关联的标签，可用于分类和过滤。
     * @return 返回一个MicrometerDeviceGatewayMonitor实例，用于监控指定设备网关。
     */
    @Override
    public DeviceGatewayMonitor getDeviceGatewayMonitor(String id, String... tags) {
        // 创建并返回一个MicrometerDeviceGatewayMonitor实例
        return new MicrometerDeviceGatewayMonitor(meterRegistryManager.getMeterRegister(GatewayTimeSeriesMetric.deviceGatewayMetric, "target"), id, tags);
    }
}

