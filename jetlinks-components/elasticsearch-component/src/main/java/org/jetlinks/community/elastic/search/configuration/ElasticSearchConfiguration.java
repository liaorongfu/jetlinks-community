package org.jetlinks.community.elastic.search.configuration;

import lombok.Generated;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.elastic.search.embedded.EmbeddedElasticSearch;
import org.jetlinks.community.elastic.search.embedded.EmbeddedElasticSearchProperties;
import org.jetlinks.community.elastic.search.index.DefaultElasticSearchIndexManager;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexManager;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexProperties;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexStrategy;
import org.jetlinks.community.elastic.search.index.strategies.DirectElasticSearchIndexStrategy;
import org.jetlinks.community.elastic.search.index.strategies.TimeByMonthElasticSearchIndexStrategy;
import org.jetlinks.community.elastic.search.service.AggregationService;
import org.jetlinks.community.elastic.search.service.ElasticSearchService;
import org.jetlinks.community.elastic.search.service.reactive.*;
import org.jetlinks.community.elastic.search.timeseries.ElasticSearchTimeSeriesManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.reactive.HostProvider;
import org.springframework.data.elasticsearch.client.reactive.RequestCreator;
import org.springframework.data.elasticsearch.client.reactive.WebClientProvider;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * ElasticSearch配置类，负责初始化和配置ElasticSearch的相关组件。
 * @author bsetfeng
 * @author zhouhao
 * @since 1.0
 **/
@Configuration(proxyBeanMethods = false)
@Slf4j
@EnableConfigurationProperties({
    EmbeddedElasticSearchProperties.class,
    ElasticSearchIndexProperties.class,
    ElasticSearchBufferProperties.class})
@AutoConfigureAfter(ReactiveElasticsearchRestClientAutoConfiguration.class)
@ConditionalOnBean(ClientConfiguration.class)
@Generated
public class ElasticSearchConfiguration {

    /**
     * 创建并返回一个默认的响应式Elasticsearch客户端。
     * 如果启用了嵌入式Elasticsearch，则会先启动嵌入式的Elasticsearch实例。
     *
     * @param embeddedProperties 嵌入式Elasticsearch的配置属性。
     * @param clientConfiguration Elasticsearch客户端的配置。
     * @return DefaultReactiveElasticsearchClient 默认的响应式Elasticsearch客户端实例。
     * @throws Exception 可能抛出的异常。
     */
    @Bean
    @SneakyThrows
    @Primary
    public DefaultReactiveElasticsearchClient defaultReactiveElasticsearchClient(EmbeddedElasticSearchProperties embeddedProperties,
                                                                                 ClientConfiguration clientConfiguration) {
        if (embeddedProperties.isEnabled()) {
            log.debug("starting embedded elasticsearch on {}:{}",
                      embeddedProperties.getHost(),
                      embeddedProperties.getPort());

            new EmbeddedElasticSearch(embeddedProperties).start();
        }
        WebClientProvider provider = getWebClientProvider(clientConfiguration);

        HostProvider<?> hostProvider = HostProvider.provider(provider,
                                                          clientConfiguration.getHeadersSupplier(),
                                                          clientConfiguration
                                                              .getEndpoints()
                                                              .toArray(new InetSocketAddress[0]));

        DefaultReactiveElasticsearchClient client =
            new DefaultReactiveElasticsearchClient(hostProvider, new RequestCreator() {
            });

        client.setHeadersSupplier(clientConfiguration.getHeadersSupplier());

        return client;
    }

    /**
     * 根据客户端配置获取WebClient提供者。
     *
     * @param clientConfiguration Elasticsearch客户端的配置。
     * @return WebClientProvider WebClient提供者实例。
     */
    private static WebClientProvider getWebClientProvider(ClientConfiguration clientConfiguration) {

        return WebClientProvider.getWebClientProvider(clientConfiguration);
    }

    /**
     * 创建并返回一个DirectElasticSearchIndexStrategy实例。
     *
     * @param elasticsearchClient Elasticsearch客户端。
     * @param indexProperties 索引的配置属性。
     * @return DirectElasticSearchIndexStrategy DirectElasticSearchIndexStrategy实例。
     */
    @Bean
    public DirectElasticSearchIndexStrategy directElasticSearchIndexStrategy(ReactiveElasticsearchClient elasticsearchClient,
                                                                             ElasticSearchIndexProperties indexProperties) {
        return new DirectElasticSearchIndexStrategy(elasticsearchClient, indexProperties);
    }

    /**
     * 创建并返回一个TimeByMonthElasticSearchIndexStrategy实例。
     *
     * @param elasticsearchClient Elasticsearch客户端。
     * @param indexProperties 索引的配置属性。
     * @return TimeByMonthElasticSearchIndexStrategy TimeByMonthElasticSearchIndexStrategy实例。
     */
    @Bean
    public TimeByMonthElasticSearchIndexStrategy timeByMonthElasticSearchIndexStrategy(ReactiveElasticsearchClient elasticsearchClient,
                                                                                       ElasticSearchIndexProperties indexProperties) {
        return new TimeByMonthElasticSearchIndexStrategy(elasticsearchClient, indexProperties);
    }

    /**
     * 创建并返回一个ElasticSearchIndexManager实例，用于管理ElasticSearch索引。
     *
     * @param strategies 索引策略列表，可自动装配。
     * @return DefaultElasticSearchIndexManager ElasticSearch索引管理器实例。
     */
    @Bean
    public DefaultElasticSearchIndexManager elasticSearchIndexManager(@Autowired(required = false) List<ElasticSearchIndexStrategy> strategies) {
        return new DefaultElasticSearchIndexManager(strategies);
    }

    /**
     * 条件性地创建并返回一个ReactiveElasticSearchService实例。
     * 只有在不存在ElasticSearchService bean时才会创建。
     *
     * @param elasticsearchClient Elasticsearch客户端。
     * @param indexManager 索引管理器。
     * @param properties Elasticsearch缓冲区配置属性。
     * @return ReactiveElasticSearchService 响应式Elasticsearch服务实例。
     */
    @Bean
    @ConditionalOnMissingBean(ElasticSearchService.class)
    public ReactiveElasticSearchService reactiveElasticSearchService(ReactiveElasticsearchClient elasticsearchClient,
                                                                     ElasticSearchIndexManager indexManager,
                                                                     ElasticSearchBufferProperties properties) {
        return new ReactiveElasticSearchService(elasticsearchClient, indexManager, properties);
    }

    /**
     * 创建并返回一个ReactiveAggregationService实例，用于支持Elasticsearch的聚合操作。
     *
     * @param indexManager 索引管理器。
     * @param restClient Elasticsearch客户端。
     * @return ReactiveAggregationService 响应式聚合服务实例。
     */
    @Bean
    public ReactiveAggregationService reactiveAggregationService(ElasticSearchIndexManager indexManager,
                                                                 ReactiveElasticsearchClient restClient) {
        return new ReactiveAggregationService(indexManager, restClient);
    }

    /**
     * 创建并返回一个ElasticSearchTimeSeriesManager实例，用于管理时间序列数据。
     *
     * @param indexManager 索引管理器。
     * @param elasticSearchService Elasticsearch服务。
     * @param aggregationService 聚合服务。
     * @return ElasticSearchTimeSeriesManager 时间序列数据管理器实例。
     */
    @Bean
    public ElasticSearchTimeSeriesManager elasticSearchTimeSeriesManager(ElasticSearchIndexManager indexManager,
                                                                         ElasticSearchService elasticSearchService,
                                                                         AggregationService aggregationService) {
        return new ElasticSearchTimeSeriesManager(indexManager, elasticSearchService, aggregationService);
    }

}

