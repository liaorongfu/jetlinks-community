package org.jetlinks.community.elastic.search.aggreation.metrics;

import lombok.*;
import org.jetlinks.community.elastic.search.aggreation.enums.MetricsType;

import java.util.Map;

/**
 * MetricsResponse类用于存储和处理度量指标的响应数据。
 * 它可以通过多种方式来获取度量指标的结果，既可以作为单一值，也可以作为多个指标类型的映射。
 * @author bsetfeng
 * @since 1.0
 **/
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MetricsResponse {

    // 存储具体度量类型到其对应值的映射
    private Map<MetricsType, MetricsResponseSingleValue> results;

    // 存储单一的度量指标结果，如果结果只有一项，可以使用此属性进行快速访问
    private MetricsResponseSingleValue singleResult;

    /**
     * 获取单一结果值。如果singleResult未初始化，则从results中提取第一个元素的值来初始化它。
     * 如果results为空，则返回一个空的MetricsResponseSingleValue对象。
     *
     * @return singleResult的值，如果未设置则基于results计算并返回。
     */
    public MetricsResponseSingleValue getSingleResult() {
        if (singleResult == null) {
            // 如果singleResult为空，则尝试从results中提取第一个元素的值来初始化
            this.singleResult = results.entrySet()
                    .stream()
                    .findFirst() // 尝试获取第一个元素
                    .map(Map.Entry::getValue) // 将键值对映射到值
                    .orElse(MetricsResponseSingleValue.empty()); // 如果没有元素，则使用空值
        }
        return singleResult;
    }
}

