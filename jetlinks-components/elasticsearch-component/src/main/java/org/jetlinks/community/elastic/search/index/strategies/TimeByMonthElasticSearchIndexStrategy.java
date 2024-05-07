package org.jetlinks.community.elastic.search.index.strategies;

import org.jetlinks.community.elastic.search.index.ElasticSearchIndexProperties;
import org.jetlinks.community.elastic.search.service.reactive.ReactiveElasticsearchClient;

import java.time.LocalDate;

/**
 * 按月对来划分索引策略
 * 时间基于月份的ElasticSearch索引策略类，继承自TemplateElasticSearchIndexStrategy。
 * 这个类提供了根据当前月份创建索引名称的逻辑。
 * @author zhouhao
 * @since 1.0
 */
public class TimeByMonthElasticSearchIndexStrategy extends TemplateElasticSearchIndexStrategy {

    /**
     * 构造函数
     *
     * @param client ReactiveElasticsearchClient对象，用于与Elasticsearch进行交互。
     * @param properties ElasticSearchIndexProperties对象，包含索引的配置信息。
     */
    public TimeByMonthElasticSearchIndexStrategy(ReactiveElasticsearchClient client, ElasticSearchIndexProperties properties) {
        super("time-by-month", client, properties); // 调用父类构造函数，设置索引策略名称、Elasticsearch客户端和索引配置。
    }

    /**
     * 根据当前时间生成保存文档时要使用的索引名称。
     *
     * @param index 原始索引名称。
     * @return 根据当前年份和月份格式化后的索引名称。
     */
    @Override
    public String getIndexForSave(String index) {
        LocalDate now = LocalDate.now(); // 获取当前日期
        String idx = wrapIndex(index); // 将原始索引名称进行封装，防止包含非法字符
        // 返回格式化后的索引名称，包含原始索引名、当前年份和月份
        return idx + "_" + now.getYear() + "-" + now.getMonthValue();
    }
}

