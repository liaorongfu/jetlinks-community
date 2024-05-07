package org.jetlinks.community.gateway.spring;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.logger.ReactiveLogger;
import org.hswebframework.web.utils.TemplateParser;
import org.jetlinks.community.gateway.annotation.Subscribe;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import org.jetlinks.core.trace.MonoTracer;
import org.jetlinks.core.utils.TopicUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
@Slf4j
@AllArgsConstructor
/**
 * Spring消息Broker，实现BeanPostProcessor接口，用于在Spring Bean初始化后处理订阅事件。
 */
public class SpringMessageBroker implements BeanPostProcessor {

    private final EventBus eventBus;
    private final Environment environment;

    /**
     * 在Bean初始化后处理订阅事件。
     *
     * @param bean 待处理的Spring Bean实例。
     * @param beanName Spring Bean的名字。
     * @return 处理后的Bean实例。
     * @throws BeansException 如果处理中发生错误。
     */
    @Override
    public Object postProcessAfterInitialization(@Nonnull Object bean, @Nonnull String beanName) throws BeansException {
        Class<?> type = ClassUtils.getUserClass(bean);
        // 遍历Bean的所有方法，查找并处理@Subscribe注解的方法
        ReflectionUtils.doWithMethods(type, method -> {
            AnnotationAttributes subscribes = AnnotatedElementUtils.getMergedAnnotationAttributes(method, Subscribe.class);
            // 如果方法没有@Subscribe注解，则跳过
            if (CollectionUtils.isEmpty(subscribes)) {
                return;
            }
            // 计算订阅的ID，优先使用注解指定的ID，若无则生成默认ID
            String id = subscribes.getString("id");
            if (!StringUtils.hasText(id)) {
                id = type.getSimpleName().concat(".").concat(method.getName());
            }
            // 准备跟踪名称和调用名称
            String traceName = "/java/" + type.getSimpleName() + "/" + method.getName();
            String callName = type.getSimpleName() + "." + method.getName();
            // 构建订阅信息
            Subscription subscription = Subscription
                .builder()
                .subscriberId("spring:" + id)
                .topics(Arrays.stream(subscribes.getStringArray("value"))
                              .map(this::convertTopic)
                              .collect(Collectors.toList()))
                .priority(subscribes.getNumber("priority"))
                .features((Subscription.Feature[]) subscribes.get("features"))
                .build();

            // 创建消息监听器
            ProxyMessageListener listener = new ProxyMessageListener(bean, method);

            // 定义错误日志记录器
            Consumer<Throwable> logError = error -> log.error("handle[{}] event message error : {}", listener, error.getLocalizedMessage(), error);

            // 创建跟踪Mono
            MonoTracer<Void> tracer = MonoTracer.create(traceName);

            // 订阅消息
            eventBus
                .subscribe(subscription, msg -> {
                    try {
                        // 处理消息并添加跟踪、错误处理和检查点
                        return listener
                            .onMessage(msg)
                            .as(tracer)
                            .doOnError(logError)
                            .checkpoint(callName);
                    } catch (Throwable e) {
                        logError.accept(e);
                    }
                    return Mono.empty();
                });

        });

        return bean;
    }

    /**
     * 转换订阅主题，支持从环境变量动态解析主题。
     *
     * @param topic 待转换的主题字符串。
     * @return 转换后的主题字符串。
     */
    protected String convertTopic(String topic) {
        // 如果主题不包含占位符，则直接返回
        if (!topic.contains("${")) {
            return topic;
        }
        // 动态解析主题
        return TemplateParser.parse(topic, template -> {
            String[] arr = template.split(":", 2);
            String property = environment.getProperty(arr[0], arr.length > 1 ? arr[1] : "");
            // 如果无法从环境变量获取属性，则抛出异常
            if (StringUtils.isEmpty(property)) {
                throw new IllegalArgumentException("Parse topic [" + template + "] error, can not get property : " + arr[0]);
            }
            return property;
        });
    }

}

