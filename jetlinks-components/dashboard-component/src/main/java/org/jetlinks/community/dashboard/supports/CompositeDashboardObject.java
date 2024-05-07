package org.jetlinks.community.dashboard.supports;

import org.apache.commons.collections.CollectionUtils;
import org.jetlinks.community.dashboard.DashboardObject;
import org.jetlinks.community.dashboard.Measurement;
import org.jetlinks.community.dashboard.ObjectDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 复合仪表板对象类，实现仪表板对象接口。能够聚合多个数据提供者，并提供统一的接口来获取定义和度量。
 */
class CompositeDashboardObject implements DashboardObject {

    private ObjectDefinition definition; // 仪表板对象的定义

    // 使用线程安全的列表来保存测量提供者
    private List<MeasurementProvider> providers = new CopyOnWriteArrayList<>();

    /**
     * 添加一个测量提供者到复合仪表板对象。
     * 如果当前还没有定义，则从新添加的提供者中获取定义。
     *
     * @param provider 要添加的测量提供者
     */
    public void addProvider(MeasurementProvider provider) {
        if (definition == null) {
            definition = provider.getObjectDefinition(); // 从第一个添加的提供者获取定义
        }
        providers.add(provider); // 添加提供者
    }

    /**
     * 获取仪表板对象的定义。
     *
     * @return 返回仪表板对象的定义
     */
    @Override
    public ObjectDefinition getDefinition() {
        return definition;
    }

    /**
     * 获取所有测量值的流。
     * 通过聚合所有提供者的测量值来提供一个统一的流。
     *
     * @return 返回一个包含所有提供者测量值的Flux流
     */
    @Override
    public Flux<Measurement> getMeasurements() {
        return Flux.fromIterable(providers)
            .flatMap(MeasurementProvider::getMeasurements);
    }

    /**
     * 根据指定ID获取测量值。
     * 遍历所有提供者，尝试获取指定ID的测量值。如果找到，则封装为CompositeMeasurement返回。
     *
     * @param id 指定的测量值ID
     * @return 返回一个包含指定ID测量值的Mono对象，如果没有找到则返回空Mono。
     */
    @Override
    public Mono<Measurement> getMeasurement(String id) {
        return Flux.fromIterable(providers)
            .flatMap(provider -> provider.getMeasurement(id))
            .collectList()
            .filter(CollectionUtils::isNotEmpty) // 确保结果非空
            .map(CompositeMeasurement::new); // 封装为CompositeMeasurement
    }
}

