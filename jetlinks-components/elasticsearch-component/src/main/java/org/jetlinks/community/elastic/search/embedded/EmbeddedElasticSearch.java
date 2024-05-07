package org.jetlinks.community.elastic.search.embedded;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.transport.Netty4Plugin;

import java.util.Collections;

/**
 * 用于启动嵌入式的Elasticsearch服务。
 * 这个类扩展了Elasticsearch的Node类，以便在应用程序内部启动一个单节点的Elasticsearch服务。
 */
@Slf4j
public class EmbeddedElasticSearch extends Node {

    // 在静态块中设置系统属性，以避免在启动时进行CPU核心数的检测
    static {
        System.setProperty("es.set.netty.runtime.available.processors","false");
    }

    /**
     * 构造函数，用于初始化嵌入式的Elasticsearch节点。
     *
     * @param properties 用于配置嵌入式Elasticsearch的属性，例如节点名称、发现类型等。
     *                    这些属性可以通过调用EmbeddedElasticSearchProperties的applySetting方法来设置。
     */
    @SneakyThrows
    public EmbeddedElasticSearch(EmbeddedElasticSearchProperties properties) {
        // 使用提供的配置属性构建Elasticsearch的Settings对象，并初始化一个单节点Elasticsearch环境
        super(InternalSettingsPreparer.prepareEnvironment(
            properties.applySetting(
                Settings.builder()
                    // 设置节点名称、发现类型、传输类型、HTTP类型、网络主机和HTTP端口
                    .put("node.name", "test")
                    .put("discovery.type", "single-node")
                    .put("transport.type", Netty4Plugin.NETTY_TRANSPORT_NAME)
                    .put("http.type", Netty4Plugin.NETTY_HTTP_TRANSPORT_NAME)
                    .put("network.host", "0.0.0.0")
                    .put("http.port", 9200)
            ).build(), Collections.emptyMap(), null, () -> "default"),
            Collections.singleton(Netty4Plugin.class), false);
    }

}

