package org.jetlinks.community.elastic.search.timeseries;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.hswebframework.ezorm.core.param.QueryParam;
import org.hswebframework.web.api.crud.entity.PagerResult;
import org.jetlinks.community.elastic.search.service.AggregationService;
import org.jetlinks.community.elastic.search.service.ElasticSearchService;
import org.jetlinks.community.timeseries.TimeSeriesData;
import org.jetlinks.community.timeseries.TimeSeriesService;
import org.jetlinks.community.timeseries.query.AggregationData;
import org.jetlinks.community.timeseries.query.AggregationQueryParam;
import org.jetlinks.core.metadata.types.DateTimeType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@AllArgsConstructor
@Slf4j
public class ElasticSearchTimeSeriesService implements TimeSeriesService {

    private final String[] index;

    private final ElasticSearchService elasticSearchService;

    private final AggregationService aggregationService;

    static DateTimeType timeType = DateTimeType.GLOBAL;

    /**
     * 根据查询参数查询时间序列数据。
     *
     * @param queryParam 查询参数，包含对时间序列数据的具体查询条件。
     * @return 返回一个Flux流，其中包含查询结果的时间序列数据。
     */
    @Override
    public Flux<TimeSeriesData> query(QueryParam queryParam) {
        // 使用Elasticsearch服务执行查询，传入索引名称、排序条件和结果映射函数
        // 查询结果通过映射函数转换为TimeSeriesData对象流
        return elasticSearchService.query(index, applySort(queryParam), map -> TimeSeriesData.of(timeType.convert(map.get("timestamp")), map));
    }

    /**
     * 多指标查询方法。
     * 本方法用于同时查询多个时间序列数据，根据收集的一组查询参数进行。
     * 查询结果将按照特定的时间类型转换为时间序列数据对象。
     *
     * @param query 查询参数的集合，每个参数定义了特定的查询条件。
     * @return 返回一个Flux流，包含查询结果的时间序列数据。
     */
    @Override
    public Flux<TimeSeriesData> multiQuery(Collection<QueryParam> query) {
        // 使用Elasticsearch服务执行多指标查询，传入索引名称、处理后的查询参数列表，
        // 以及一个映射函数，将查询结果映射为时间序列数据对象。
        return elasticSearchService.multiQuery(
            index,
            query.stream().peek(this::applySort).collect(Collectors.toList()),
            map -> TimeSeriesData.of(timeType.convert(map.get("timestamp")), map));
    }


    /**
     * 根据查询参数计算指定索引下的文档数量。
     *
     * 此方法重写了父类方法，旨在利用Elasticsearch服务来查询特定索引的文档数量。
     * 它接受一个QueryParam对象作为输入，该对象可能包含用于过滤或限定查询范围的参数。
     * 方法内部，查询参数被传递给elasticSearchService的count方法，该方法返回一个表示文档数量的Mono对象。
     * 最后，通过map方法将Long类型的结果转换为Integer类型，以符合方法签名的返回类型要求。
     *
     * @param queryParam 查询参数，用于限定查询的范围或条件。
     * @return Mono<Integer> 表示查询结果的文档数量，以Mono形式返回。
     */
    @Override
    public Mono<Integer> count(QueryParam queryParam) {
        return elasticSearchService
            .count(index, queryParam)
            .map(Long::intValue);
    }


    /**
     * 根据查询参数查询时间序列数据的分页结果。
     *
     * 此方法通过调用Elasticsearch服务来实现时间序列数据的分页查询。它接收一个查询参数对象，
     * 使用该参数来调整排序方式，并最终返回一个包含时间序列数据的分页结果。
     *
     * @param queryParam 查询参数，包含查询条件和分页信息。
     * @return 返回一个Mono对象，该对象表示一个异步计算过程，计算结果是一个包含时间序列数据的分页结果。
     */
    @Override
    public Mono<PagerResult<TimeSeriesData>> queryPager(QueryParam queryParam) {
        // 调用elasticSearchService的queryPager方法进行分页查询
        // 其中，index指定索引名称，applySort(queryParam)用于应用排序条件，map转换函数用于将查询结果映射为TimeSeriesData对象
        return elasticSearchService.queryPager(index, applySort(queryParam), map -> TimeSeriesData.of(timeType.convert(map.get("timestamp")), map));
    }


    /**
     * 根据查询参数查询分页结果。
     *
     * 该方法通过调用Elasticsearch服务，根据提供的查询参数和映射函数，查询并返回分页结果。
     * 查询结果会被转换为指定类型的分页对象Mono<PagerResult<T>>，用于反应式编程中。
     *
     * @param queryParam 查询参数，包含分页和过滤条件等。
     * @param mapper 映射函数，用于将查询到的时序数据映射为指定的业务类型T。
     * @param <T> 映射后的数据类型。
     * @return 返回一个Mono对象，包含分页查询结果。
     */
    @Override
    public <T> Mono<PagerResult<T>> queryPager(QueryParam queryParam, Function<TimeSeriesData, T> mapper) {
        // 调用Elasticsearch服务的查询分页方法，传入索引名称、排序策略和映射函数
        // 其中，排序策略通过applySort方法根据查询参数生成，映射函数用于将时序数据转换为指定类型
        return elasticSearchService.queryPager(index, applySort(queryParam), map -> mapper.apply(TimeSeriesData.of(timeType.convert(map.get("timestamp")), map)));
    }


    @Override
    public Flux<AggregationData> aggregation(AggregationQueryParam queryParam) {
        return aggregationService
            .aggregation(index, queryParam)
            .onErrorResume(err -> {
                log.error(err.getMessage(), err);
                return Mono.empty();
            })
            .map(AggregationData::of);

    }

    protected QueryParam applySort(QueryParam param) {
        if (CollectionUtils.isEmpty(param.getSorts())) {
            param.orderBy("timestamp").desc();
        }
        return param;
    }


    @Override
    public Mono<Void> commit(Publisher<TimeSeriesData> data) {
        return Flux.from(data)
            .flatMap(this::commit)
            .then();
    }

    @Override
    public Mono<Void> commit(TimeSeriesData data) {
        Map<String, Object> mapData = data.getData();
        mapData.put("timestamp", data.getTimestamp());
        return elasticSearchService.commit(index[0], mapData);
    }

    @Override
    public Mono<Void> save(Publisher<TimeSeriesData> dateList) {

        return elasticSearchService.save(index[0],
            Flux.from(dateList)
                .map(data -> {
                    Map<String, Object> mapData = data.getData();
                    mapData.put("timestamp", data.getTimestamp());
                    return mapData;
                }));
    }
}
