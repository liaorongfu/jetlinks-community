package org.jetlinks.community.elastic.search.aggreation.bucket;

import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.*;
import org.jetlinks.community.elastic.search.aggreation.metrics.MetricsResponseSingleValue;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AggregationResponseHandle 类提供了一系列静态方法，用于处理和转换不同的聚合响应数据。
 * @author bsetfeng
 * @since 1.0
 **/
public class AggregationResponseHandle {

    /**
     * 根据 Terms 聚合类型处理并转换数据。
     *
     * @param a Terms 类型的聚合对象。
     * @return 转换后的 Bucket 列表。
     */
    public static <A extends Aggregation> List<Bucket> terms(A a) {
        Terms terms = (Terms) a;
        return terms.getBuckets()
            .stream()
            .map(b -> {
                // 构建并返回一个新的 Bucket 实例，包含从 Terms 聚合中提取的数据。
                Bucket bucket = Bucket.builder()
                    .key(b.getKeyAsString())
                    .count(b.getDocCount())
                    .name(a.getName())
                    .build();
                b.getAggregations().asList()
                    .forEach(subAggregation -> route(bucket, subAggregation));
                return bucket;
            }).collect(Collectors.toList())
            ;
    }

    /**
     * 根据 Range 聚合类型处理并转换数据。
     *
     * @param a Range 类型的聚合对象。
     * @return 转换后的 Bucket 列表。
     */
    public static <A extends Aggregation> List<Bucket> range(A a) {
        Range range = (Range) a;
        return range.getBuckets()
            .stream()
            .map(b -> {
                // 构建并返回一个新的 Bucket 实例，包含从 Range 聚合中提取的数据。
                Bucket bucket = Bucket.builder()
                    .key(b.getKeyAsString())
                    .from(b.getFrom())
                    .to(b.getTo())
                    .fromAsString(b.getFromAsString())
                    .toAsString(b.getToAsString())
                    .count(b.getDocCount()).build();
                b.getAggregations().asList()
                    .forEach(subAggregation -> {
                        route(bucket, subAggregation);
                    });
                return bucket;
            }).collect(Collectors.toList())
            ;
    }

    /**
     * 处理 DateHistogram 聚合类型的数据，调用 bucketsHandle 方法进行具体的处理。
     *
     * @param a DateHistogram 类型的聚合对象。
     * @return 转换后的 Bucket 列表。
     */
    public static <A extends Aggregation> List<Bucket> dateHistogram(A a) {
        Histogram histogram = (Histogram) a;
        return bucketsHandle(histogram.getBuckets(), a.getName());
    }

    /**
     * 通用的 Bucket 处理方法，用于处理 Histogram 类型的聚合数据。
     *
     * @param buckets Histogram 聚合的 Bucket 列表。
     * @param name 聚合的名称。
     * @return 转换后的 Bucket 列表。
     */
    private static List<Bucket> bucketsHandle(List<? extends Histogram.Bucket> buckets, String name) {
        return buckets
            .stream()
            .map(b -> {
                // 构建并返回一个新的 Bucket 实例，包含从 Histogram 聚合中提取的数据。
                Bucket bucket = Bucket.builder()
                    .key(b.getKeyAsString())
                    .count(b.getDocCount())
                    .name(name)
                    .build();
                b.getAggregations().asList()
                    .forEach(subAggregation -> route(bucket, subAggregation));
                return bucket;
            }).collect(Collectors.toList())
            ;
    }

    /**
     * 根据不同的子聚合类型，为 Bucket 对象设置相应的子聚合结果。
     *
     * @param bucket 目标 Bucket 对象。
     * @param a 子聚合对象。
     */
    private static <A extends Aggregation> void route(Bucket bucket, A a) {
        // 根据不同的聚合类型，调用相应的方法处理子聚合数据。
        if (a instanceof Terms) {
            bucket.setBuckets(terms(a));
        } else if (a instanceof Range) {
            bucket.setBuckets(range(a));
        } else if (a instanceof Histogram) {
            bucket.setBuckets(range(a));
        } else if (a instanceof Avg) {
            bucket.setAvg(avg(a));
        } else if (a instanceof Min) {
            bucket.setMin(min(a));
        } else if (a instanceof Max) {
            bucket.setMax(max(a));
        } else if (a instanceof Sum) {
            bucket.setSum(sum(a));
        } else if (a instanceof Stats) {
            stats(bucket, a);
        }  else if (a instanceof ValueCount) {
            bucket.setValueCount(count(a));
        } else {
            throw new UnsupportedOperationException("不支持的聚合类型");
        }
    }

    /**
     * 处理 ValueCount 聚合类型的数据，生成 MetricsResponseSingleValue 对象。
     *
     * @param a ValueCount 类型的聚合对象。
     * @return 构建后的 MetricsResponseSingleValue 实例。
     */
    public static <A extends Aggregation> MetricsResponseSingleValue count(A a) {
        ValueCount max = (ValueCount) a;
        return MetricsResponseSingleValue.builder()
            .value(max.getValue())
            .name(a.getName())
            .valueAsString(max.getValueAsString())
            .build();
    }

    /**
     * 处理 Avg 聚合类型的数据，生成 MetricsResponseSingleValue 对象。
     *
     * @param a Avg 类型的聚合对象。
     * @return 构建后的 MetricsResponseSingleValue 实例。
     */
    public static <A extends Aggregation> MetricsResponseSingleValue avg(A a) {
        Avg avg = (Avg) a;
        return MetricsResponseSingleValue.builder()
            .value(avg.getValue())
            .name(a.getName())
            .valueAsString(avg.getValueAsString())
            .build();
    }

    /**
     * 处理 Max 聚合类型的数据，生成 MetricsResponseSingleValue 对象。
     *
     * @param a Max 类型的聚合对象。
     * @return 构建后的 MetricsResponseSingleValue 实例。
     */
    public static <A extends Aggregation> MetricsResponseSingleValue max(A a) {
        Max max = (Max) a;
        return MetricsResponseSingleValue.builder()
            .value(max.getValue())
            .name(a.getName())
            .valueAsString(max.getValueAsString())
            .build();
    }

    /**
     * 处理 Min 聚合类型的数据，生成 MetricsResponseSingleValue 对象。
     *
     * @param a Min 类型的聚合对象。
     * @return 构建后的 MetricsResponseSingleValue 实例。
     */
    public static <A extends Aggregation> MetricsResponseSingleValue min(A a) {
        Min min = (Min) a;
        return MetricsResponseSingleValue.builder()
            .value(min.getValue())
            .name(a.getName())
            .valueAsString(min.getValueAsString())
            .build();
    }

    /**
     * 处理 Sum 聚合类型的数据，生成 MetricsResponseSingleValue 对象。
     *
     * @param a Sum 类型的聚合对象。
     * @return 构建后的 MetricsResponseSingleValue 实例。
     */
    public static <A extends Aggregation> MetricsResponseSingleValue sum(A a) {
        Sum sum = (Sum) a;
        return MetricsResponseSingleValue.builder()
            .value(sum.getValue())
            .name(a.getName())
            .valueAsString(sum.getValueAsString())
            .build();
    }

    /**
     * 处理 Stats 聚合类型的数据，为 Bucket 对象设置相关的统计结果。
     *
     * @param bucket 目标 Bucket 对象。
     * @param a Stats 类型的聚合对象。
     */
    public static <A extends Aggregation> void stats(Bucket bucket, A a) {
        Stats stats = (Stats) a;
        bucket.setAvg(MetricsResponseSingleValue.builder()
            .value(stats.getAvg())
            .name(a.getName())
            .valueAsString(stats.getAvgAsString())
            .build());
        bucket.setMax(MetricsResponseSingleValue.builder()
            .value(stats.getMax())
            .name(a.getName())
            .valueAsString(stats.getMaxAsString())
            .build());
        bucket.setMin(MetricsResponseSingleValue.builder()
            .value(stats.getMin())
            .name(a.getName())
            .valueAsString(stats.getMinAsString())
            .build());
        bucket.setSum(MetricsResponseSingleValue.builder()
            .value(stats.getSum())
            .name(a.getName())
            .valueAsString(stats.getSumAsString())
            .build());
    }
}

