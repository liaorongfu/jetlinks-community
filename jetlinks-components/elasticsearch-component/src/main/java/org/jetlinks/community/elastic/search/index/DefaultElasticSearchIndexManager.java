package org.jetlinks.community.elastic.search.index;

import lombok.Generated;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * 默认的ElasticSearch索引管理器，负责管理ElasticSearch索引的策略和元数据。
 * 该类可以通过配置前缀为"elasticsearch.index"的属性来定制。
 */
@ConfigurationProperties(prefix = "elasticsearch.index")
public class DefaultElasticSearchIndexManager implements ElasticSearchIndexManager {

    /**
     * 默认的索引策略名称。如果未指定特定索引的策略，将使用此默认策略。
     */
    @Getter
    @Setter
    @Generated
    private String defaultStrategy = "direct";

    /**
     * 用于存储索引名称到其对应策略名称的映射。索引名称是不区分大小写的。
     */
    @Getter
    @Setter
    @Generated
    private Map<String, String> indexUseStrategy = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);

    /**
     * 注册的ElasticSearch索引策略映射，键为策略ID，值为策略实例。
     */
    private final Map<String, ElasticSearchIndexStrategy> strategies = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);

    /**
     * 存储索引的元数据，键为索引名称，值为索引元数据。不区分索引名称的大小写。
     */
    private final Map<String, ElasticSearchIndexMetadata> indexMetadataStore = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);

    /**
     * 构造函数，接收可选的ElasticSearch索引策略列表。
     * @param strategies ElasticSearch索引策略列表，允许为空。
     */
    public DefaultElasticSearchIndexManager(@Autowired(required = false) List<ElasticSearchIndexStrategy> strategies) {
        if (strategies != null) {
            strategies.forEach(this::registerStrategy);
        }
    }

    /**
     * 创建或更新ElasticSearch索引。
     * @param index 索引的元数据。
     * @return 返回一个Mono<Void>，表示异步操作完成。
     */
    @Override
    public Mono<Void> putIndex(ElasticSearchIndexMetadata index) {
        return this.getIndexStrategy(index.getIndex())
                   .flatMap(strategy -> strategy.putIndex(index))
                   .doOnNext(idx -> indexMetadataStore.put(idx.getIndex(), idx))
                   .then();
    }

    /**
     * 获取指定索引的元数据。
     * @param index 索引名称。
     * @return 返回一个包含ElasticSearch索引元数据的Mono对象。
     */
    @Override
    public Mono<ElasticSearchIndexMetadata> getIndexMetadata(String index) {
        return Mono.justOrEmpty(indexMetadataStore.get(index))
                   .switchIfEmpty(Mono.defer(() -> doLoadMetaData(index)
                       .doOnNext(metadata -> indexMetadataStore.put(metadata.getIndex(), metadata))));
    }

    /**
     * 从策略中加载指定索引的元数据。
     * @param index 索引名称。
     * @return 返回一个包含索引元数据的Mono对象。
     */
    protected Mono<ElasticSearchIndexMetadata> doLoadMetaData(String index) {
        return getIndexStrategy(index)
            .flatMap(strategy -> strategy.loadIndexMetadata(index));
    }

    /**
     * 获取指定索引使用的策略。
     * @param index 索引名称。
     * @return 返回一个包含指定索引策略的Mono对象。
     * @throws IllegalArgumentException 如果指定索引没有配置任何策略。
     */
    @Override
    public Mono<ElasticSearchIndexStrategy> getIndexStrategy(String index) {
        return Mono.justOrEmpty(strategies.get(indexUseStrategy.getOrDefault(index.toLowerCase(), defaultStrategy)))
                   .switchIfEmpty(Mono.error(() -> new IllegalArgumentException("[" + index + "] 不支持任何索引策略")));
    }

    /**
     * 为指定索引指定使用的策略。
     * @param index 索引名称。
     * @param strategy 策略名称。
     */
    @Override
    public void useStrategy(String index, String strategy) {
        indexUseStrategy.put(index, strategy);
    }

    /**
     * 注册一个ElasticSearch索引策略。
     * @param strategy 索引策略实例。
     */
    public void registerStrategy(ElasticSearchIndexStrategy strategy) {
        strategies.put(strategy.getId(), strategy);
    }

}

