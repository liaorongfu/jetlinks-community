package org.jetlinks.community.logging.access;

import org.hswebframework.web.logging.events.AccessLoggerAfterEvent;
import org.jetlinks.community.logging.configuration.LoggingProperties;
import org.jetlinks.core.utils.TopicUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 用于处理访问日志转换的组件。
 */
@Component
public class AccessLoggingTranslator {

    // 用于发布事件的应用事件发布器
    private final ApplicationEventPublisher eventPublisher;

    // 日志配置属性
    private final LoggingProperties properties;

    /**
     * 构造函数。
     *
     * @param eventPublisher 用于发布事件的应用事件发布器。
     * @param properties 日志配置属性，包含访问日志的配置。
     */
    public AccessLoggingTranslator(ApplicationEventPublisher eventPublisher, LoggingProperties properties) {
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * 当接收到访问日志后事件时，转换并发布访问日志。
     *
     * @param event 访问日志后事件，包含访问日志详情。
     */
    @EventListener
    public void translate(AccessLoggerAfterEvent event) {
        // 检查路径是否被排除在日志记录之外
        for (String pathExclude : properties.getAccess().getPathExcludes()) {
            if (TopicUtils.match(pathExclude, event.getLogger().getUrl())) {
                return; // 如果路径匹配，则不记录日志
            }
        }
        // 发布可序列化的访问日志事件
        eventPublisher.publishEvent(SerializableAccessLog.of(event.getLogger()));
    }

}

