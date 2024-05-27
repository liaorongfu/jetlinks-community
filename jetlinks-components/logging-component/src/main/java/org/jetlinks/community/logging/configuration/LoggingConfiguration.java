package org.jetlinks.community.logging.configuration;

import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.logging.logback.SystemLoggingAppender;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.Configuration;

@Configuration // 标识为配置类，用于Spring框架的自动配置。
@EnableConfigurationProperties(LoggingProperties.class) // 启用LoggingProperties属性绑定。
@Slf4j // 使用Lombok的日志注解，方便记录日志。
public class LoggingConfiguration implements ApplicationEventPublisherAware {

    // LoggingProperties用于配置日志相关属性。
    private final LoggingProperties properties;

    /**
     * 构造函数
     * @param properties LoggingProperties实例，用于初始化日志配置。
     */
    public LoggingConfiguration(LoggingProperties properties) {
        this.properties = properties;
        // 将LoggingProperties中定义的系统日志上下文信息，复制到SystemLoggingAppender的静态上下文中。
        SystemLoggingAppender.staticContext.putAll(properties.getSystem().getContext());
    }

    /**
     * 设置应用事件发布器，用于发布日志事件。
     * @param applicationEventPublisher 应用事件发布器实例。
     */
    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        // 将应用事件发布器设置到SystemLoggingAppender中，以便可以发布日志事件。
        SystemLoggingAppender.publisher = applicationEventPublisher;
    }
}

