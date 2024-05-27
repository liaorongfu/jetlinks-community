package org.jetlinks.community.logging.event.handler;

import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.elastic.search.index.DefaultElasticSearchIndexMetadata;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexManager;
import org.jetlinks.community.elastic.search.service.ElasticSearchService;
import org.jetlinks.community.logging.access.SerializableAccessLog;
import org.jetlinks.core.metadata.types.DateTimeType;
import org.jetlinks.core.metadata.types.ObjectType;
import org.jetlinks.core.metadata.types.StringType;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * @author bsetfeng
 * @since 1.0
 **/
@Component
@Slf4j
@Order(5)
public class AccessLoggerEventHandler {

    private final ElasticSearchService elasticSearchService;


    /**
     * 构造函数：AccessLoggerEventHandler
     * 用于初始化日志事件处理器，配置ElasticSearch索引。
     *
     * @param elasticSearchService ElasticSearch服务，用于操作ElasticSearch。
     * @param indexManager 索引管理器，用于管理ElasticSearch中的索引。
     */
    public AccessLoggerEventHandler(ElasticSearchService elasticSearchService, ElasticSearchIndexManager indexManager) {
        this.elasticSearchService = elasticSearchService;

        // 配置日志索引，添加字段定义，如请求时间、响应时间、操作、IP地址、URL等。
        indexManager.putIndex(
            new DefaultElasticSearchIndexMetadata(LoggerIndexProvider.ACCESS.getIndex())
                .addProperty("requestTime", new DateTimeType())
                .addProperty("responseTime", new DateTimeType())
                .addProperty("action", new StringType())
                .addProperty("ip", new StringType())
                .addProperty("url", new StringType())
                .addProperty("httpHeaders", new ObjectType())
                // 配置上下文信息，包括用户ID和用户名，以支持更细粒度的日志记录。
                .addProperty("context", new ObjectType()
                    .addProperty("userId", new StringType())
                    .addProperty("username", new StringType())
                )
        ).subscribe(); // 订阅操作结果，确保索引配置成功。
    }


    /**
     * 处理可序列化的访问日志信息，并将其提交到ElasticSearch服务。
     * 该方法使用事件监听器模式，当接收到可序列化的访问日志信息事件时会被调用。
     *
     * @param info 可序列化的访问日志信息，该信息将被提交到ElasticSearch的访问日志索引中。
     *             要求参数info必须实现Serializable接口，以支持其序列化和反序列化操作。
     * @return 该方法没有返回值，它通过ElasticSearch服务的异步提交操作来处理日志信息。
     */
    @EventListener
    public void acceptAccessLoggerInfo(SerializableAccessLog info) {
        // 将访问日志信息提交到ElasticSearch，并订阅提交操作的结果。
        elasticSearchService.commit(LoggerIndexProvider.ACCESS, Mono.just(info)).subscribe();
    }


}
