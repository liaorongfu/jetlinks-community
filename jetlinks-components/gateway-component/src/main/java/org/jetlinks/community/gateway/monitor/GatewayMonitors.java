package org.jetlinks.community.gateway.monitor;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 网关监控管理类，用于注册和获取设备网关监控器。
 */
public class GatewayMonitors {

    // 存储所有已注册的设备网关监控器供应商
    private static final List<DeviceGatewayMonitorSupplier> deviceGatewayMonitorSuppliers = new CopyOnWriteArrayList<>();

    // 默认的无设备网关监控器实例
    static final NoneDeviceGatewayMonitor nonDevice = new NoneDeviceGatewayMonitor();


    // 静态初始化块，当前为空
    static {

    }
    /**
     * 注册一个设备网关监控器供应商。
     *
     * @param supplier 设备网关监控器供应商实例，不可为null。
     */
    public static void register(DeviceGatewayMonitorSupplier supplier) {
        deviceGatewayMonitorSuppliers.add(supplier);
    }

    /**
     * 根据给定的ID和标签私有地获取设备网关监控器。
     *
     * @param id 设备ID。
     * @param tags 设备标签，可变长参数。
     * @return 返回对应的设备网关监控器实例，如果没有匹配的监控器，则返回默认的无设备网关监控器。
     */
    private static DeviceGatewayMonitor doGetDeviceGatewayMonitor(String id, String... tags) {
        // 流式处理所有已注册的供应商，获取非null的监控器实例并收集
        List<DeviceGatewayMonitor> all = deviceGatewayMonitorSuppliers.stream()
            .map(supplier -> supplier.getDeviceGatewayMonitor(id, tags))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        // 根据收集到的监控器数量，返回对应的实例
        if (all.isEmpty()) {
            return nonDevice; // 无匹配时返回默认监控器
        }
        if (all.size() == 1) {
            return all.get(0); // 只有一个匹配时直接返回
        }
        // 多个匹配时创建并返回复合监控器
        CompositeDeviceGatewayMonitor monitor = new CompositeDeviceGatewayMonitor();
        monitor.add(all);
        return monitor;
    }

    /**
     * 公开方法，用于获取设备网关监控器，实现懒加载机制。
     *
     * @param id 设备ID。
     * @param tags 设备标签，可变长参数。
     * @return 返回对应的设备网关监控器实例，封装在LazyDeviceGatewayMonitor中以实现懒加载。
     */
    public static DeviceGatewayMonitor getDeviceGatewayMonitor(String id, String... tags) {
        return new LazyDeviceGatewayMonitor(() -> doGetDeviceGatewayMonitor(id, tags));
    }
}

