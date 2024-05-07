package org.jetlinks.community.gateway.monitor;

import java.util.function.Supplier;

/**
 * 类 LazyDeviceGatewayMonitor 实现了 DeviceGatewayMonitor 接口，
 * 用于懒惰初始化设备网关监控器。它通过一个供应函数来提供实际的 DeviceGatewayMonitor 实例，
 * 直到首次被访问时才进行初始化。
 */
class LazyDeviceGatewayMonitor implements DeviceGatewayMonitor {

    // 用于存储实际的 DeviceGatewayMonitor 实例，如果已初始化，则不会再次初始化。
    private volatile DeviceGatewayMonitor target;

    // 供应函数，用于提供 DeviceGatewayMonitor 实例。
    private Supplier<DeviceGatewayMonitor> monitorSupplier;

    /**
     * 构造函数。
     *
     * @param monitorSupplier 一个供应函数，用于在首次访问时创建 DeviceGatewayMonitor 实例。
     */
    public LazyDeviceGatewayMonitor(Supplier<DeviceGatewayMonitor> monitorSupplier) {
        this.monitorSupplier = monitorSupplier;
    }

    /**
     * 获取实际的 DeviceGatewayMonitor 实例。如果该实例尚未被初始化，
     * 则利用 monitorSupplier 提供的供应函数进行初始化。
     *
     * @return 返回实际的 DeviceGatewayMonitor 实例。
     */
    public DeviceGatewayMonitor getTarget() {
        if (target == null) {
            target = monitorSupplier.get();
        }
        return target;
    }

    // 下面的方法代理了实际的 DeviceGatewayMonitor 实例的操作。

    @Override
    public void totalConnection(long total) {
        getTarget().totalConnection(total);
    }

    @Override
    public void connected() {
        getTarget().connected();
    }

    @Override
    public void rejected() {
        getTarget().rejected();
    }

    @Override
    public void disconnected() {
        getTarget().disconnected();
    }

    @Override
    public void receivedMessage() {
        getTarget().receivedMessage();
    }

    @Override
    public void sentMessage() {
        getTarget().sentMessage();
    }
}

