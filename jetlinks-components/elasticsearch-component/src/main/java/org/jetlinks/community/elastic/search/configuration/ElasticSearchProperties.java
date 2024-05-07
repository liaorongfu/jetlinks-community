package org.jetlinks.community.elastic.search.configuration;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * ElasticSearch配置类，用于存储ElasticSearch客户端的配置信息。
 * 使用@ConfigurationProperties注解将属性绑定到配置文件中的elasticsearch.client前缀。
 */
@ConfigurationProperties(prefix = "elasticsearch.client")
@Getter
@Setter
public class ElasticSearchProperties {

    // ElasticSearch服务器的主机名，默认为localhost。
    private String host = "localhost";
    // ElasticSearch服务器的端口号，默认为9200。
    private int port = 9200;

    // 连接请求超时时间，默认为5000毫秒。
    private int connectionRequestTimeout = 5000;
    // 连接超时时间，默认为2000毫秒。
    private int connectTimeout = 2000;
    // Socket超时时间，默认为2000毫秒。
    private int socketTimeout = 2000;
    // 最大总连接数，默认为30。
    private int maxConnTotal = 30;
    // ElasticSearch主机列表，可用于配置多个主机。
    private List<String> hosts;

    /**
     * 根据配置创建HttpHost数组。
     * 如果hosts列表非空，将使用hosts列表中的每个元素创建HttpHost；
     * 否则，将使用host和port属性创建一个HttpHost。
     *
     * @return HttpHost数组，用于ElasticSearch客户端配置。
     */
    public HttpHost[] createHosts() {
        if (CollectionUtils.isEmpty(hosts)) {
            return new HttpHost[]{new HttpHost(host, port, "http")};
        }

        return hosts.stream().map(HttpHost::create).toArray(HttpHost[]::new);
    }

    /**
     * 应用请求配置到RequestConfig.Builder实例。
     * 设置连接超时、连接请求超时和Socket超时时间。
     *
     * @param builder RequestConfig.Builder实例。
     * @return 配置后的RequestConfig.Builder实例。
     */
    public RequestConfig.Builder applyRequestConfigBuilder(RequestConfig.Builder builder) {
        builder.setConnectTimeout(connectTimeout);
        builder.setConnectionRequestTimeout(connectionRequestTimeout);
        builder.setSocketTimeout(socketTimeout);

        return builder;
    }

    /**
     * 应用连接池配置到HttpAsyncClientBuilder实例。
     * 设置最大总连接数。
     *
     * @param builder HttpAsyncClientBuilder实例。
     * @return 配置后的HttpAsyncClientBuilder实例。
     */
    public HttpAsyncClientBuilder applyHttpAsyncClientBuilder(HttpAsyncClientBuilder builder) {
        builder.setMaxConnTotal(maxConnTotal);

        return builder;
    }
}

