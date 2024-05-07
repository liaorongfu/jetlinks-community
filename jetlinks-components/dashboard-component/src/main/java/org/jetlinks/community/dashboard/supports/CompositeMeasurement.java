package org.jetlinks.community.dashboard.supports;

import org.jetlinks.community.dashboard.Measurement;
import org.jetlinks.community.dashboard.MeasurementDefinition;
import org.jetlinks.community.dashboard.MeasurementDimension;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 一个组合测量类，实现了Measurement接口，用于管理和操作一组测量值。
 */
class CompositeMeasurement implements Measurement {

    private List<Measurement> measurements; // 包含所有子测量对象的列表

    private Measurement main; // 代表主要的测量对象

    /**
     * 构造函数，初始化组合测量对象。
     *
     * @param measurements 需要包含在组合测量中的测量对象列表，不能为空。
     * @throws IllegalArgumentException 如果measurements为空，抛出此异常。
     */
    public CompositeMeasurement(List<Measurement> measurements) {
        Assert.notEmpty(measurements, "measurements can not be empty");
        this.measurements = measurements;
        this.main = measurements.get(0);
    }

    /**
     * 获取测量定义。
     *
     * @return 返回主要测量对象的测量定义。
     */
    @Override
    public MeasurementDefinition getDefinition() {
        return main.getDefinition();
    }

    /**
     * 获取所有测量维度的流。
     *
     * @return 返回一个Flux流，包含所有测量对象的所有维度。
     */
    @Override
    public Flux<MeasurementDimension> getDimensions() {
        return Flux.fromIterable(measurements)
            .flatMap(Measurement::getDimensions);
    }

    /**
     * 根据ID获取测量维度。
     *
     * @param id 测量维度的ID。
     * @return 返回一个Mono对象，包含找到的第一个测量维度，如果没有找到则返回空。
     */
    @Override
    public Mono<MeasurementDimension> getDimension(String id) {
        return Flux.fromIterable(measurements)
            .flatMap(measurement -> measurement.getDimension(id))
            .next();
    }
}

