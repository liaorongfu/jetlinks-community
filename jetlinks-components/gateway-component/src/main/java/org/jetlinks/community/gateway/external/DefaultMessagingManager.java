package org.jetlinks.community.gateway.external;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认消息管理器，负责消息的订阅与管理。
 * 实现了MessagingManager接口和BeanPostProcessor接口，
 * 以便于在整个应用中统一处理消息订阅，并且能够自动注册订阅提供者。
 */
@Component
public class DefaultMessagingManager implements MessagingManager, BeanPostProcessor {

    // 使用ConcurrentHashMap来存储订阅提供者，以支持并发和动态注册
    private final Map<String, SubscriptionProvider> subProvider = new ConcurrentHashMap<>();

    // 使用AntPathMatcher来匹配订阅请求的topic和订阅提供者支持的模式
    private final static PathMatcher matcher = new AntPathMatcher();

    /**
     * 订阅指定主题的消息。
     *
     * @param request 订阅请求，包含订阅的主题和其它相关信息。
     * @return 返回一个Flux流，该流会发出订阅的消息。
     */
    @Override
    public Flux<Message> subscribe(SubscribeRequest request) {

        return Flux.defer(() -> {
            // 遍历所有已注册的订阅提供者，寻找能够处理该订阅请求的提供者
            for (Map.Entry<String, SubscriptionProvider> entry : subProvider.entrySet()) {
                if (matcher.match(entry.getKey(), request.getTopic())) {
                    // 如果找到匹配的提供者，则通过该提供者订阅消息，并处理返回结果
                    return entry.getValue()
                        .subscribe(request)
                        .map(v -> {
                            // 如果返回的结果是Message类型则直接返回，否则封装为成功消息
                            if (v instanceof Message) {
                                return ((Message) v);
                            }
                            return Message.success(request.getId(), request.getTopic(), v);
                        });
                }
            }

            // 如果没有找到能够处理订阅请求的提供者，则返回一个错误的Flux
            return Flux.error(new UnsupportedOperationException("不支持的topic"));
        });
    }

    /**
     * 注册一个订阅提供者。
     *
     * @param provider 要注册的订阅提供者。
     */
    public void register(SubscriptionProvider provider) {
        // 为该订阅提供者注册所有支持的订阅主题模式
        for (String pattern : provider.getTopicPattern()) {
            subProvider.put(pattern, provider);
        }
    }

    /**
     * Spring框架回调方法，用于在bean初始化之后进行处理。
     * 该方法会检查每个bean是否为SubscriptionProvider类型，
     * 如果是，则自动注册到消息管理器中。
     *
     * @param bean 刚刚被初始化的bean实例。
     * @param beanName bean的名称。
     * @return 返回处理后的bean实例。
     * @throws BeansException 如果处理中发生错误。
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 如果bean是SubscriptionProvider类型，则自动注册
        if (bean instanceof SubscriptionProvider) {
            register(((SubscriptionProvider) bean));
        }
        return bean;
    }
}

