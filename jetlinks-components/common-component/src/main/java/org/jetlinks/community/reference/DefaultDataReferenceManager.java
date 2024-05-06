package org.jetlinks.community.reference;

import org.jetlinks.community.strategy.StaticStrategyManager;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 默认数据引用管理器，继承自StaticStrategyManager，实现了DataReferenceManager接口。
 * 用于管理数据引用提供者，并提供查询数据是否被引用以及获取数据引用信息的功能。
 */
public class DefaultDataReferenceManager extends StaticStrategyManager<DataReferenceProvider> implements DataReferenceManager {

    /**
     * 检查指定的数据是否被引用。
     *
     * @param dataType 数据类型。
     * @param dataId 数据ID。
     * @return 返回一个Mono<Boolean>，如果数据被引用则返回true，否则返回false。
     */
    @Override
    public Mono<Boolean> isReferenced(String dataType, String dataId) {
        // 通过查询引用信息并检查是否有元素来判断数据是否被引用
        return this
            .getReferences(dataType, dataId)
            .hasElements();
    }

    /**
     * 获取指定数据类型的特定数据ID的所有引用信息。
     *
     * @param dataType 数据类型。
     * @param dataId 数据ID。
     * @return 返回一个Flux<DataReferenceInfo>，流式返回所有引用信息。
     */
    @Override
    public Flux<DataReferenceInfo> getReferences(String dataType, String dataId) {
        // 对指定数据ID调用提供者的getReference方法，获取引用信息
        return doWithFlux(dataType, provider -> provider.getReference(dataId));
    }

    /**
     * 获取指定数据类型的所有引用信息。
     *
     * @param dataType 数据类型。
     * @return 返回一个Flux<DataReferenceInfo>，流式返回所有引用信息。
     */
    @Override
    public Flux<DataReferenceInfo> getReferences(String dataType) {
        // 对指定数据类型调用提供者的getReferences方法，获取所有引用信息
        return doWithFlux(dataType, DataReferenceProvider::getReferences);
    }
}

