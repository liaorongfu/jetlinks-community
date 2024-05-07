package org.jetlinks.community.elastic.search.embedded;

import lombok.Getter;
import lombok.Setter;
import org.elasticsearch.common.settings.Settings;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用于配置嵌入式Elasticsearch服务的属性类。
 * 通过配置文件中的"elasticsearch.embedded"前缀来映射属性。
 */
@ConfigurationProperties(prefix = "elasticsearch.embedded")
@Getter
@Setter
public class EmbeddedElasticSearchProperties {

    // 是否启用嵌入式的Elasticsearch服务
    private boolean enabled;

    // Elasticsearch数据存储路径，默认值为"./data/elasticsearch"
    private String dataPath = "./data/elasticsearch";

    // Elasticsearch的Home路径，默认值为"./"
    private String homePath = "./";

    // Elasticsearch监听的端口号，默认值为9200
    private int port = 9200;

    // Elasticsearch监听的主机地址，默认值为"0.0.0.0"
    private String host = "0.0.0.0";

    /**
     * 将当前配置的属性应用到Elasticsearch的Settings中。
     * @param settings Elasticsearch的Settings构建器
     * @return 应用了当前配置的Settings构建器
     */
    public Settings.Builder applySetting(Settings.Builder settings) {
        // 应用Elasticsearch的配置：网络主机、HTTP端口、数据路径、Home路径
        return settings.put("network.host", host)
            .put("http.port", port)
            .put("path.data", dataPath)
            .put("path.home", homePath);
    }
}

