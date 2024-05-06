package org.jetlinks.community.micrometer;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 管理和提供MeterRegistry实例的组件。
 * 允许通过metric名称和tag keys动态获取MeterRegistry实例。
 **/
@Component
@Setter
public class MeterRegistryManager {

    // 使用ConcurrentHashMap来存储MeterRegistry实例，以支持并发和线程安全
    private Map<String, MeterRegistry> meterRegistryMap = new ConcurrentHashMap<>();

    // 通过@Autowired自动注入MeterRegistrySupplier的列表，用于动态创建MeterRegistry实例
    @Autowired
    private List<MeterRegistrySupplier> suppliers;

    /**
     * 创建一个CompositeMeterRegistry实例，它聚合了所有MeterRegistrySupplier提供的MeterRegistry。
     *
     * @param metric 要创建的Meter的名称。
     * @param tagKeys 要应用于Meter的标签键。
     * @return 新创建的CompositeMeterRegistry实例。
     **/
    private MeterRegistry createMeterRegistry(String metric, String... tagKeys) {
        return new CompositeMeterRegistry(Clock.SYSTEM,
            suppliers.stream()
                .map(supplier -> supplier.getMeterRegistry(metric, tagKeys))
                .collect(Collectors.toList()));
    }

    /**
     * 根据metric名称和tag keys获取对应的MeterRegistry实例。
     * 如果不存在，则使用createMeterRegistry方法创建一个新的实例，并添加到meterRegistryMap中。
     *
     * @param metric 要获取的Meter的名称。
     * @param tagKeys 要应用于Meter的标签键。
     * @return 对应的MeterRegistry实例。
     **/
    public MeterRegistry getMeterRegister(String metric, String... tagKeys) {
        return meterRegistryMap.computeIfAbsent(metric, _metric -> createMeterRegistry(_metric, tagKeys));
    }

}

