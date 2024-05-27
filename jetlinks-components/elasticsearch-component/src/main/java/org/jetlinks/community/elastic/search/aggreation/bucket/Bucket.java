package org.jetlinks.community.elastic.search.aggreation.bucket;

import lombok.*;
import org.jetlinks.community.elastic.search.aggreation.metrics.MetricsResponseSingleValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bucket类用于存储统计结果的数据桶。
 * 它包含了数据桶的基本信息，如键、名、计数以及统计范围（从、到），
 * 还包括了各种统计指标（总和、值计数、平均值、最小值、最大值）及其对应的数值。
 * @author bsetfeng
 * @since 1.0
 **/
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Bucket {

    private String key; // 数据桶的键
    private String name; // 数据桶的名称
    private long count; // 数据桶中的计数
    private String fromAsString; // "从"值的字符串表示
    private Object from; // 数据桶的起始值
    private String toAsString; // "到"值的字符串表示
    private Object to; // 数据桶的结束值
    private MetricsResponseSingleValue sum; // 数据桶的总和指标
    private MetricsResponseSingleValue valueCount; // 数据桶的值计数指标
    private MetricsResponseSingleValue avg; // 数据桶的平均值指标
    private MetricsResponseSingleValue min; // 数据桶的最小值指标
    private MetricsResponseSingleValue max; // 数据桶的最大值指标
    private List<Bucket> buckets; // 子数据桶列表

    /**
     * 将传入的数值转换为0或其本身。
     * 如果传入的数值为无穷大或NaN，则转换为0；否则返回其本身。
     * @param number 需要转换的数值
     * @return 转换后的数值，无穷大或NaN则为0
     **/
    private double toNumber(double number) {
        return (Double.isInfinite(number) || Double.isNaN(number)) ? 0 : number;
    }

    /**
     * 将当前Bucket实例中的统计指标数值转换为Map形式。
     * 仅包含非null的指标及其对应的数值。
     * @return 包含统计指标名称和数值的Map
     **/
    public Map<String, Number> toMap() {
        Map<String, Number> map = new HashMap<>();
        // 为非null的指标添加到map中
        if (this.sum != null) {
            map.put(sum.getName(), toNumber(sum.getValue()));
        }
        if (this.valueCount != null) {
            map.put(valueCount.getName(), toNumber(valueCount.getValue()));
        }
        if (this.avg != null) {
            map.put(avg.getName(), toNumber(avg.getValue()));
        }
        if (this.min != null) {
            map.put(min.getName(), toNumber(min.getValue()));
        }
        if (this.max != null) {
            map.put(max.getName(), toNumber(max.getValue()));
        }
        return map;
    }
}

