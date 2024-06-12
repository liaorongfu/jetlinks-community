package org.jetlinks.community.elastic.search.timeseries;

import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.elastic.search.index.DefaultElasticSearchIndexMetadata;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexManager;
import org.jetlinks.community.elastic.search.service.AggregationService;
import org.jetlinks.community.elastic.search.service.ElasticSearchService;
import org.jetlinks.community.timeseries.TimeSeriesManager;
import org.jetlinks.community.timeseries.TimeSeriesMetadata;
import org.jetlinks.community.timeseries.TimeSeriesMetric;
import org.jetlinks.community.timeseries.TimeSeriesService;
import org.jetlinks.core.metadata.SimplePropertyMetadata;
import org.jetlinks.core.metadata.types.DateTimeType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author bsetfeng
 * @author zhouhao
 * @since 1.0
 **/
@Slf4j
public class ElasticSearchTimeSeriesManager implements TimeSeriesManager {


    /**
     * 存储时间序列服务的映射，键为指标ID，值为对应的时间序列服务。
     * 使用ConcurrentHashMap以确保线程安全。
     */
    private final Map<String, TimeSeriesService> serviceMap = new ConcurrentHashMap<>(16);

    /**
     * 索引管理器，用于注册和管理时间序列的索引元数据。
     */
    protected final ElasticSearchIndexManager indexManager;

    /**
     * ElasticSearch服务，用于执行ElasticSearch相关操作，如数据存储。
     */
    private final ElasticSearchService elasticSearchService;

    /**
     * 聚合服务，用于执行时间序列数据的聚合操作。
     */
    private final AggregationService aggregationService;


    /**
     * 构造函数，初始化ElasticSearchTimeSeriesManager。
     *
     * @param indexManager 索引管理器，用于管理ElasticSearch索引。
     * @param elasticSearchService ElasticSearch服务，用于与ElasticSearch交互。
     * @param aggregationService 聚合服务，用于执行时间序列数据的聚合操作。
     */
    public ElasticSearchTimeSeriesManager(ElasticSearchIndexManager indexManager,
                                          ElasticSearchService elasticSearchService,
                                          AggregationService aggregationService) {
        this.elasticSearchService = elasticSearchService;
        this.indexManager = indexManager;
        this.aggregationService = aggregationService;
    }

    /**
     * 根据指标获取时间序列服务。
     *
     * 此方法通过指标的ID来获取对应的时间序列服务。它是一个覆盖父类方法的实现，
     * 旨在提供更具体的获取服务的方式，即通过指标而非直接使用ID。
     *
     * @param metric 指标对象，包含时间序列的标识信息。
     * @return 时间序列服务，用于处理特定时间序列的相关操作。
     */
    @Override
    public TimeSeriesService getService(TimeSeriesMetric metric) {
        // 通过指标的ID获取时间序列服务
        return getService(metric.getId());
    }

    /**
     * 根据时间序列指标获取服务实例。
     * 此方法通过将时间序列指标转换为指标ID数组来实现服务的获取。这种方法允许灵活地处理多个指标，
     * 并通过它们的ID来定位和获取相关联的服务。
     *
     * @param metric 时间序列指标数组，用于描述需要获取服务的特定指标。
     * @return 返回一个时间序列服务实例，该实例与传入的指标相关联。
     */
    @Override
    public TimeSeriesService getServices(TimeSeriesMetric... metric) {
        // 将时间序列指标数组转换为指标ID的字符串数组
        return getServices(Arrays
            .stream(metric)
            .map(TimeSeriesMetric::getId).toArray(String[]::new));
    }


    /**
     * 根据指定的指标获取时间序列服务。
     *
     * 此方法通过创建一个新的ElasticSearchTimeSeriesService实例来提供针对特定指标的时间序列服务。
     * 它利用ElasticSearchService和AggregationService来实现对时间序列数据的存储和聚合操作。
     *
     * @param metric 一个可变参数，用于指定需要的时间序列指标。这些指标将用于筛选和操作特定的数据集。
     * @return 返回一个TimeSeriesService实例，该实例针对指定的指标进行了配置，可用于执行时间序列数据分析和操作。
     */
    @Override
    public TimeSeriesService getServices(String... metric) {
        return new ElasticSearchTimeSeriesService(metric, elasticSearchService, aggregationService);
    }


    /**
     * 根据指标名称获取时间序列服务。
     * 如果对应的服务不存在，则根据指标名称创建并添加一个新的ElasticSearch时间序列服务。
     * 这个方法利用了服务映射的并发安全性，确保在高并发环境下也能正确地获取或创建服务。
     *
     * @param metric 指标名称，用于唯一标识一个时间序列服务。
     * @return 对应的時間序列服务实例，如果不存在则为新创建的ElasticSearch时间序列服务。
     */
    @Override
    public TimeSeriesService getService(String metric) {
        // 使用computeIfAbsent方法，如果metric对应的服務不存在，則使用lambda表達式創建新的ElasticSearchTimeSeriesService
        return serviceMap.computeIfAbsent(metric,
            id -> new ElasticSearchTimeSeriesService(new String[]{id}, elasticSearchService, aggregationService));
    }



    /**
     * 注册时间序列元数据到索引管理器。
     *
     * 此方法通过创建一个包含时间戳属性的ElasticSearch索引元数据对象，并将其添加到索引管理器中来注册时间序列元数据。
     * 时间戳属性被定义为一个简单的属性，其ID为"timestamp"，值类型为日期时间类型。
     * 元数据的度量ID和属性被用于构建索引元数据对象。
     *
     * @param metadata 时间序列元数据对象，包含要注册的元数据信息。
     * @return 返回一个Mono<Void>对象，表示异步操作的结果。
     */
    @Override
    public Mono<Void> registerMetadata(TimeSeriesMetadata metadata) {
        // 创建一个简单属性元数据对象用于表示时间戳
        //默认字段
        SimplePropertyMetadata timestamp = new SimplePropertyMetadata();
        timestamp.setId("timestamp");
        timestamp.setValueType(new DateTimeType());

        // 构建ElasticSearch索引元数据对象，并添加时间戳属性
        // 然后将索引元数据对象添加到索引管理器中
        return indexManager.putIndex(new DefaultElasticSearchIndexMetadata(metadata.getMetric().getId(), metadata.getProperties())
            .addProperty(timestamp));
    }


}
