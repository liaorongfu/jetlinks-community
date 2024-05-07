package org.jetlinks.community.elastic.search.index.strategies;

import org.jetlinks.community.elastic.search.index.ElasticSearchIndexMetadata;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexProperties;
import org.jetlinks.community.elastic.search.service.reactive.ReactiveElasticsearchClient;
import reactor.core.publisher.Mono;

/**
 * 直接ElasticSearch索引策略类，继承自AbstractElasticSearchIndexStrategy。提供直接的索引操作策略。
 */
public class DirectElasticSearchIndexStrategy extends AbstractElasticSearchIndexStrategy {

    public static String ID = "direct"; // 策略ID，标识为"direct"。

    /**
     * 构造函数
     * @param client ReactorElasticsearchClient客户端，用于与Elasticsearch进行交互。
     * @param properties ElasticSearchIndexProperties索引配置属性，包含索引的配置信息。
     */
    public DirectElasticSearchIndexStrategy(ReactiveElasticsearchClient client, ElasticSearchIndexProperties properties) {
        super(ID, client, properties); // 调用父类构造函数，初始化索引策略。
    }

    /**
     * 获取保存操作使用的索引名称。
     * @param index 原始索引名称。
     * @return 包装后的索引名称。
     */
    @Override
    public String getIndexForSave(String index) {
        return wrapIndex(index); // 对索引名称进行包装。
    }

    /**
     * 获取搜索操作使用的索引名称。
     * @param index 原始索引名称。
     * @return 包装后的索引名称。
     */
    @Override
    public String getIndexForSearch(String index) {
        return wrapIndex(index); // 对索引名称进行包装。
    }

    /**
     * 创建或更新索引。
     * @param metadata 要创建或更新的索引元数据。
     * @return Mono<ElasticSearchIndexMetadata> 异步返回创建或更新后的索引元数据。
     */
    @Override
    public Mono<ElasticSearchIndexMetadata> putIndex(ElasticSearchIndexMetadata metadata) {
        // 生成新的索引名称并进行操作，最后返回操作后的索引元数据。
        ElasticSearchIndexMetadata index = metadata.newIndexName(wrapIndex(metadata.getIndex()));
        return doPutIndex(index, false)
            .thenReturn(index);
    }

    /**
     * 加载索引的元数据。
     * @param index 索引名称。
     * @return Mono<ElasticSearchIndexMetadata> 异步返回索引的元数据。
     */
    @Override
    public Mono<ElasticSearchIndexMetadata> loadIndexMetadata(String index) {
        return doLoadIndexMetadata(index); // 加载指定索引的元数据。
    }

}

