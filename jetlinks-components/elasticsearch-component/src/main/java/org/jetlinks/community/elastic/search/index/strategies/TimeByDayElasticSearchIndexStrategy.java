package org.jetlinks.community.elastic.search.index.strategies;

import org.hswebframework.utils.time.DateFormatter;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexProperties;
import org.jetlinks.community.elastic.search.service.reactive.ReactiveElasticsearchClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Date;

/**
 * 按日期来划分索引策略
 * 基于天的時間索引策略，用于Elasticsearch。此策略按照天生成和使用索引。
 * 继承自TemplateElasticSearchIndexStrategy，提供特定于时间的索引名称生成逻辑。
 * @author caizz
 * @since 1.0
 */
@Component
public class TimeByDayElasticSearchIndexStrategy extends TemplateElasticSearchIndexStrategy {

    /**
     * 构造函数
     *
     * @param client Elasticsearch的响应式客户端，用于与Elasticsearch进行交互。
     * @param properties Elasticsearch索引的配置属性，包含索引的配置信息。
     */
    public TimeByDayElasticSearchIndexStrategy(ReactiveElasticsearchClient client, ElasticSearchIndexProperties properties) {
        super("time-by-day", client, properties); // 初始化基类，设置索引策略标识、Elasticsearch客户端和配置属性。
    }

    /**
     * 根据当前时间生成保存文档时要使用的索引名称。
     *
     * @param index 原始索引名称。
     * @return 生成的索引名称，包含日期信息，格式为"原始索引名_年-月-日"。
     */
    @Override
    public String getIndexForSave(String index) {
        LocalDate now = LocalDate.now(); // 获取当前日期
        String idx = wrapIndex(index); // 封装原始索引名称，防止特殊字符影响

        // 生成并返回带日期的索引名称
        return idx + "_" + now.getYear()
            + "-" + (now.getMonthValue() < 10 ? "0" : "") + now.getMonthValue() // 添加年份和月份，确保月份两位数显示
            + "-" + (now.getDayOfMonth() < 10 ? "0" : "") + now.getDayOfMonth(); // 添加日期，确保日期两位数显示
    }
}

