package org.jetlinks.community.logging.event.handler;

import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.elastic.search.index.DefaultElasticSearchIndexMetadata;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexManager;
import org.jetlinks.community.elastic.search.service.ElasticSearchService;
import org.jetlinks.community.logging.system.SerializableSystemLog;
import org.jetlinks.core.event.EventBus;
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
public class SystemLoggerEventHandler {

    private final EventBus eventBus;

    private final ElasticSearchService elasticSearchService;

    /**
     * 构造函数用于初始化SystemLoggerEventHandler。
     * 它通过提供的参数配置ElasticSearch索引，并订阅事件以处理日志事件。
     *
     * @param elasticSearchService 用于与ElasticSearch交互的服务。
     * @param indexManager 索引管理器，用于管理ElasticSearch索引。
     * @param eventBus 事件总线，用于发布和订阅事件。
     */
    public SystemLoggerEventHandler(ElasticSearchService elasticSearchService,
                                    ElasticSearchIndexManager indexManager,
                                    EventBus eventBus) {
        this.elasticSearchService = elasticSearchService;
        this.eventBus = eventBus;

        // 配置日志索引的元数据，包括日志的各种字段类型定义。
        indexManager.putIndex(
            new DefaultElasticSearchIndexMetadata(LoggerIndexProvider.SYSTEM.getIndex())
                .addProperty("createTime", new DateTimeType()) // 日志创建时间
                .addProperty("name", new StringType()) // 日志名称
                .addProperty("level", new StringType()) // 日志级别
                .addProperty("message", new StringType()) // 日志消息
                .addProperty("className", new StringType()) // 发生日志的类名
                .addProperty("exceptionStack", new StringType()) // 异常栈信息
                .addProperty("methodName", new StringType()) // 发生日志的方法名
                .addProperty("threadId", new StringType()) // 线程ID
                .addProperty("threadName", new StringType()) // 线程名称
                .addProperty("id", new StringType()) // 日志唯一ID
                .addProperty("context", new ObjectType() // 日志上下文信息，包含requestId和server信息
                    .addProperty("requestId", new StringType())
                    .addProperty("server", new StringType()))
        ).subscribe(); // 订阅操作结果，确保索引配置成功。
    }


    /**
     * 处理可序列化的系统日志信息事件。
     * 该方法会将日志信息发布到事件总线，并提交到ElasticSearch服务进行存储。
     *
     * @param info 可序列化的系统日志信息对象。包含日志名称、级别等信息。
     */
    @EventListener
    public void acceptAccessLoggerInfo(SerializableSystemLog info) {
        // 将日志信息发布到事件总线，日志名称中的点被替换为斜杠，日志级别转为小写，以确定事件的路由。
        eventBus
            .publish("/logging/system/" + info.getName().replace(".", "/") + "/" + (info.getLevel().toLowerCase()), info)
            .subscribe();
        // 将日志信息提交到ElasticSearch服务，指定索引为系统日志索引，然后订阅操作完成的信号。
        elasticSearchService.commit(LoggerIndexProvider.SYSTEM, Mono.just(info))
            .subscribe();
    }

}
