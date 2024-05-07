package org.jetlinks.community.dashboard.supports;

import lombok.Getter;
import org.jetlinks.community.dashboard.Measurement;
import org.jetlinks.community.dashboard.MeasurementDefinition;
import org.jetlinks.community.dashboard.MeasurementDimension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StaticMeasurement 类实现了 Measurement 接口，用于创建和管理静态测量值。
 */
public class StaticMeasurement implements Measurement {

    /**
     * 测量定义，包含测量的基本信息。
     */
    @Getter
    private MeasurementDefinition definition;

    /**
     * 使用 ConcurrentHashMap 来存放测量维度，确保线程安全的添加和访问。
     */
    private Map<String, MeasurementDimension> dimensions = new ConcurrentHashMap<>();

    /**
     * 构造函数，初始化一个静态测量实例。
     * @param definition 测量定义对象，包含测量的基本信息。
     */
    public StaticMeasurement(MeasurementDefinition definition) {
        this.definition = definition;
    }

    /**
     * 添加一个测量维度到当前测量实例。
     * @param dimension 测量维度对象，包含具体的测量单位和数值。
     * @return 返回当前 StaticMeasurement 实例，支持链式调用。
     */
    public StaticMeasurement addDimension(MeasurementDimension dimension) {

        // 将测量维度添加到 dimensions 映射中
        dimensions.put(dimension.getDefinition().getId(), dimension);

        return this;

    }

    /**
     * 获取所有测量维度的 Flux 流。
     * @return 返回一个包含所有测量维度的 Flux 流。
     */
    @Override
    public Flux<MeasurementDimension> getDimensions() {
        // 从 dimensions 映射中获取所有值，转换为 Flux 流返回
        return Flux.fromIterable(dimensions.values());
    }

    /**
     * 根据维度 ID 获取对应的测量维度。
     * @param id 测量维度的唯一标识符。
     * @return 如果找到对应的测量维度，则返回一个包含该维度的 Mono，否则返回一个空的 Mono。
     */
    @Override
    public Mono<MeasurementDimension> getDimension(String id) {
        // 从 dimensions 映射中获取指定 ID 的测量维度，使用 Mono 返回
        return Mono.justOrEmpty(dimensions.get(id));
    }
}

