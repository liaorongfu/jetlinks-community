package org.jetlinks.community.elastic.search.aggreation.bucket;

import lombok.*;
import org.elasticsearch.search.aggregations.bucket.histogram.LongBounds;
import org.hswebframework.utils.StringUtils;
import org.jetlinks.community.elastic.search.aggreation.enums.BucketType;
import org.jetlinks.community.elastic.search.aggreation.metrics.MetricsAggregationStructure;

import java.util.LinkedList;
import java.util.List;

/**
 * 分桶聚合结构类，用于定义和存储分桶聚合的相关信息。
 * 支持不同的分桶类型，如terms、date_histogram等。
 * @author bsetfeng
 * @since 1.0
 **/
@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BucketAggregationsStructure {

    @NonNull // 指定字段不能为空
    private String field;

    private String name; // 指定分桶的名称

    @NonNull // 分桶类型不能为空
    private BucketType type = BucketType.TERMS; // 默认分桶类型为TERMS

    /**
     * 指定返回分组的数量。如果未指定，将由搜索引擎决定。
     */
    private Integer size;

    private Sort sort; // 指定分桶的排序方式

    private List<Ranges> ranges; // 用于范围分桶时定义的范围列表

    private LongBounds extendedBounds; // 用于日期分桶时指定的扩展边界

    /**
     * 时间格式，仅在类型为日期分桶时使用。
     */
    private String format;

    /**
     * 单位时间间隔，用于日期直方图分桶。详见DateHistogramInterval。
     */
    private String interval;

    /**
     * 缺失值的处理方式，如果文档中缺少指定字段，将使用此值进行聚合。
     */
    private Object missingValue;

    // 子指标聚合配置列表，用于在分桶内进行进一步的指标聚合
    private List<MetricsAggregationStructure> subMetricsAggregation = new LinkedList<>();

    // 子分桶聚合配置列表，用于在当前分桶基础上进行更细粒度的分桶聚合
    private List<BucketAggregationsStructure> subBucketAggregation = new LinkedList<>();

    /**
     * 获取分桶的名称。如果未显式设置名称，则默认使用分桶类型和字段名拼接生成。
     * @return 分桶的名称
     */
    public String getName() {
        if (StringUtils.isNullOrEmpty(name)) {
            name = type.name().concat("_").concat(field);
        }
        return name;
    }
}

