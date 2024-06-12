package org.jetlinks.community.device.measurements.message;

import io.micrometer.core.instrument.MeterRegistry;
import org.jetlinks.community.dashboard.supports.StaticMeasurementProvider;
import org.jetlinks.community.device.measurements.DeviceDashboardDefinition;
import org.jetlinks.community.device.measurements.DeviceObjectDefinition;
import org.jetlinks.community.device.timeseries.DeviceTimeSeriesMetric;
import org.jetlinks.community.gateway.annotation.Subscribe;
import org.jetlinks.community.micrometer.MeterRegistryManager;
import org.jetlinks.community.timeseries.TimeSeriesManager;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.message.DeviceMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DeviceMessageMeasurementProvider extends StaticMeasurementProvider {

    MeterRegistry registry;

    public DeviceMessageMeasurementProvider(EventBus eventBus,
                                            MeterRegistryManager registryManager,
                                            TimeSeriesManager timeSeriesManager) {
        super(DeviceDashboardDefinition.instance, DeviceObjectDefinition.message);

        registry = registryManager.getMeterRegister(DeviceTimeSeriesMetric.deviceMetrics().getId(),
            "target", "msgType", "productId");

        addMeasurement(new DeviceMessageMeasurement(eventBus, timeSeriesManager));

    }

    /**
     * 订阅设备消息事件。
     *
     * 通过注解@Subscribe指定订阅的事件主题模式，此方法将处理匹配 '' 模式的所有事件。
     * 主题模式的使用允许方法对一类特定格式的事件进行响应，增强了代码的灵活性和可维护性。
     *
     * @param message 事件携带的设备消息。设备消息包含与特定设备相关的信息，
     *                 如设备ID、消息内容、时间戳等，这些信息用于后续的业务处理或统计。
     * @return 返回一个Mono<Void>对象，表示异步处理结果。使用Mono.fromRunnable将业务逻辑包装为一个Runnable，
     *         然后在后台线程中执行，这样可以实现非阻塞的异步处理，提高系统的响应能力和吞吐量。
     *
     * 方法的主要作用是接收设备消息，并通过指标系统对消息进行计数。这样做可以实时监控设备消息的数量，
     * 为系统的运营和监控提供数据支持。通过将消息的某些属性转换为标签（tags），可以实现对不同类型或来源的消息进行分类统计，
     * 提高统计的灵活性和针对性。
     */
    @Subscribe("/device/*/*/message/**")
    public Mono<Void> incrementMessage(DeviceMessage message) {
        return Mono.fromRunnable(() -> {
            registry
                .counter("message-count", convertTags(message))
                .increment();
        });
    }


    static final String[] empty = new String[0];

    /**
     * 将设备消息转换为特定格式的标签数组。
     * 此方法主要用于从设备消息中提取产品ID，并将其格式化为一个字符串数组。
     * 如果消息为null，则返回一个空数组。
     * 如果产品ID不存在，则默认为"unknown"。
     *
     * @param message 设备消息对象，可能为null。
     * @return 包含产品ID的字符串数组。如果消息为null，数组长度为1，元素为"unknown"。
     */
    private String[] convertTags(DeviceMessage message) {
        if (message == null) {
            return empty;
        }
        return new String[]{
            "productId", message.getHeader("productId").map(String::valueOf).orElse("unknown")
        };
    }

}
