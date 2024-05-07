package org.jetlinks.community.elastic.search.index;

import lombok.*;
import org.elasticsearch.common.settings.Settings;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ElasticSearch索引配置类，用于存储ElasticSearch索引的设置。
 * 该类使用Lombok的注解来简化对象的创建和访问。
 * 通过@ConfigurationProperties注解将其与Spring Boot的配置文件绑定，前缀为"elasticsearch.index.settings"。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "elasticsearch.index.settings")
public class ElasticSearchIndexProperties {

    /**
     * 索引的分片数，默认为1。可以通过配置文件设置。
     * 分片是ElasticSearch中将数据拆分到多个节点的一种方式。
     */
    private int numberOfShards = 1;

    /**
     * 索引的副本数，默认为0。可以通过配置文件设置。
     * 副本是ElasticSearch中数据的冗余备份，用于提高查询性能和数据恢复速度。
     */
    private int numberOfReplicas = 0;

    /**
     * 将当前配置转换为ElasticSearch的Settings对象。
     * 这个方法将numberOfShards和numberOfReplicas配置转换为ElasticSearch理解的格式。
     *
     * @return Settings对象，包含了ElasticSearch索引的配置设置。
     */
    public Settings toSettings() {

        // 构建ElasticSearch的Settings对象，确保分片数至少为1，并设置副本数。
        return Settings.builder()
            .put("number_of_shards", Math.max(1, numberOfShards)) // 确保分片数不会被设置为小于1
            .put("number_of_replicas", numberOfReplicas) // 直接设置副本数
            .build();
    }
}

