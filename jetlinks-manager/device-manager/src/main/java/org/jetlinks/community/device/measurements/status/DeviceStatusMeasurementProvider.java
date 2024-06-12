package org.jetlinks.community.device.measurements.status;

import io.micrometer.core.instrument.MeterRegistry;
import org.jetlinks.community.dashboard.supports.StaticMeasurementProvider;
import org.jetlinks.community.device.measurements.DeviceDashboardDefinition;
import org.jetlinks.community.device.measurements.DeviceObjectDefinition;
import org.jetlinks.community.device.service.LocalDeviceInstanceService;
import org.jetlinks.community.device.timeseries.DeviceTimeSeriesMetric;
import org.jetlinks.community.gateway.annotation.Subscribe;
import org.jetlinks.community.micrometer.MeterRegistryManager;
import org.jetlinks.community.timeseries.TimeSeriesManager;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.message.DeviceMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 继承自StaticMeasurementProvider，用于提供设备状态测量的实现类。
 * 该类通过注册计量器来收集设备的在线和离线状态数据。
 */
@Component
public class DeviceStatusMeasurementProvider extends StaticMeasurementProvider {

    // 记录设备状态的计量器注册表
    private final MeterRegistry registry;

    /**
     * 构造函数初始化DeviceStatusMeasurementProvider。
     *
     * @param registryManager 管理计量器注册表的类，用于获取特定计量器注册表。
     * @param instanceService 本地设备实例服务，用于设备状态记录测量。
     * @param timeSeriesManager 时间序列管理器，用于设备状态变化测量。
     * @param eventBus 事件总线，用于订阅设备状态变化事件。
     */
    public DeviceStatusMeasurementProvider(MeterRegistryManager registryManager,
                                           LocalDeviceInstanceService instanceService,
                                           TimeSeriesManager timeSeriesManager,
                                           EventBus eventBus) {
        super(DeviceDashboardDefinition.instance, DeviceObjectDefinition.status);

        // 添加设备状态变化和状态记录的测量项
        addMeasurement(new DeviceStatusChangeMeasurement(timeSeriesManager, eventBus));
        addMeasurement(new DeviceStatusRecordMeasurement(instanceService, timeSeriesManager));

        // 根据设备时间序列指标的ID和特定标签获取或创建计量器注册表
        registry = registryManager.getMeterRegister(DeviceTimeSeriesMetric.deviceMetrics().getId(),
                                                    "target", "msgType", "productId");
    }

    /**
     * 订阅设备在线事件，并增加对应产品的在线计数器。
     *
     * @param msg 设备消息，包含设备信息和状态。
     * @return Mono<Void> 表示异步操作的结果。
     */
    @Subscribe("/device/*/*/online")
    public Mono<Void> incrementOnline(DeviceMessage msg) {
        return Mono.fromRunnable(() -> {
            // 解析产品ID，并增加对应产品的在线计数器
            String productId = parseProductId(msg);
            registry.counter("online", "productId", productId).increment();
        });
    }

    /**
     * 订阅设备离线事件，并增加对应产品的离线计数器。
     *
     * @param msg 设备消息，包含设备信息和状态。
     * @return Mono<Void> 表示异步操作的结果。
     */
    @Subscribe("/device/*/*/offline")
    public Mono<Void> incrementOffline(DeviceMessage msg) {
        return Mono.fromRunnable(() -> {
            // 解析产品ID，并增加对应产品的离线计数器
            String productId = parseProductId(msg);
            registry.counter("offline", "productId", productId).increment();
        });
    }

    /**
     * 从设备消息中解析产品ID。
     * 如果消息头中不存在产品ID，则默认返回"unknown"。
     *
     * @param msg 设备消息。
     * @return 产品ID字符串。
     */
    private String parseProductId(DeviceMessage msg) {
        return msg.getHeader("productId")
            .map(String::valueOf)
            .orElse("unknown");
    }
}

