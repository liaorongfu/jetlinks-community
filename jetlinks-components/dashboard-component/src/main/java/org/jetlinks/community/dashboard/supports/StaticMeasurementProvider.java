package org.jetlinks.community.dashboard.supports;

import lombok.Getter;
import org.jetlinks.community.dashboard.DashboardDefinition;
import org.jetlinks.community.dashboard.Measurement;
import org.jetlinks.community.dashboard.ObjectDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一个抽象类，用于提供静态测量值的提供者。
 * 实现了MeasurementProvider接口，用于获取测量值。
 */
public abstract class StaticMeasurementProvider implements MeasurementProvider {

    // 使用ConcurrentHashMap来存储测量值，以支持并发的读写操作。
    private Map<String, Measurement> measurements = new ConcurrentHashMap<>();

    // 获取仪表板定义
    @Getter
    private DashboardDefinition dashboardDefinition;
    // 获取对象定义
    @Getter
    private ObjectDefinition objectDefinition;

    /**
     * 构造函数，初始化仪表板定义和对象定义。
     *
     * @param dashboardDefinition 仪表板定义，用于配置仪表板的相关信息。
     * @param objectDefinition 对象定义，用于配置对象的相关信息。
     */
    public StaticMeasurementProvider(DashboardDefinition dashboardDefinition,
                                     ObjectDefinition objectDefinition) {
        this.dashboardDefinition = dashboardDefinition;
        this.objectDefinition = objectDefinition;
    }

    /**
     * 添加一个测量值到测量值集合中。
     *
     * @param measurement 测量值对象，包含了具体的测量信息。
     */
    protected void addMeasurement(Measurement measurement) {
        measurements.put(measurement.getDefinition().getId(), measurement);
    }

    /**
     * 获取所有的测量值，以Flux流的形式返回。
     *
     * @return 返回一个包含所有测量值的Flux流。
     */
    @Override
    public Flux<Measurement> getMeasurements() {
        return Flux.fromIterable(measurements.values());
    }

    /**
     * 根据测量值的ID获取特定的测量值。
     *
     * @param id 测量值的唯一标识符。
     * @return 返回一个包含指定测量值的Mono对象，如果找不到则返回空Mono。
     */
    @Override
    public Mono<Measurement> getMeasurement(String id) {
        return Mono.justOrEmpty(measurements.get(id));
    }
}

