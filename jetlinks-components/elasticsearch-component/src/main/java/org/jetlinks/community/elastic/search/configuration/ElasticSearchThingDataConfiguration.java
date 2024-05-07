package org.jetlinks.community.elastic.search.configuration;

import org.jetlinks.core.things.ThingsRegistry;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexManager;
import org.jetlinks.community.elastic.search.service.AggregationService;
import org.jetlinks.community.elastic.search.service.ElasticSearchService;
import org.jetlinks.community.elastic.search.things.ElasticSearchColumnModeStrategy;
import org.jetlinks.community.elastic.search.things.ElasticSearchRowModeStrategy;
import org.jetlinks.community.things.data.ThingsDataRepositoryStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(ThingsDataRepositoryStrategy.class)
/**
 * ElasticSearchThingDataConfiguration类负责配置ElasticSearch相关的策略 bean。
 * 这个配置类只在类路径上存在ThingsDataRepositoryStrategy类时被激活。
 */
public class ElasticSearchThingDataConfiguration {

    /**
     * 定义并返回一个ElasticSearch列模式策略的bean实例。
     * 这个策略利用ElasticSearch进行列模式的数据处理。
     *
     * @param registry 事物注册表，用于访问系统中的事物定义。
     * @param searchService ElasticSearch搜索服务，提供搜索功能。
     * @param aggregationService 聚合服务，用于对数据进行聚合操作。
     * @param indexManager ElasticSearch索引管理器，负责索引的创建和管理。
     * @return ElasticSearchColumnModeStrategy的实例，用于列模式的数据处理策略。
     */
    @Bean
    public ElasticSearchColumnModeStrategy elasticSearchColumnModThingDataPolicy(
        ThingsRegistry registry,
        ElasticSearchService searchService,
        AggregationService aggregationService,
        ElasticSearchIndexManager indexManager) {

        return new ElasticSearchColumnModeStrategy(registry, searchService, aggregationService, indexManager);
    }

    /**
     * 定义并返回一个ElasticSearch行模式策略的bean实例。
     * 这个策略利用ElasticSearch进行行模式的数据处理。
     *
     * @param registry 事物注册表，用于访问系统中的事物定义。
     * @param searchService ElasticSearch搜索服务，提供搜索功能。
     * @param aggregationService 聚合服务，用于对数据进行聚合操作。
     * @param indexManager ElasticSearch索引管理器，负责索引的创建和管理。
     * @return ElasticSearchRowModeStrategy的实例，用于行模式的数据处理策略。
     */
    @Bean
    public ElasticSearchRowModeStrategy elasticSearchRowModThingDataPolicy(
        ThingsRegistry registry,
        ElasticSearchService searchService,
        AggregationService aggregationService,
        ElasticSearchIndexManager indexManager) {

        return new ElasticSearchRowModeStrategy(registry, searchService, aggregationService, indexManager);
    }
}

