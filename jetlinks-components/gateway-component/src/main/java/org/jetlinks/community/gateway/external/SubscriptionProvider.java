package org.jetlinks.community.gateway.external;

import reactor.core.publisher.Flux;

/**
 * 订阅提供者接口。定义了订阅服务的基本行为，允许实现不同的订阅策略和管理订阅请求。
 */
public interface SubscriptionProvider {

    /**
     * 获取订阅提供者的唯一标识符。
     *
     * @return 返回订阅提供者的唯一标识符，类型为String。
     */
    String id();

    /**
     * 获取订阅提供者的名称。
     *
     * @return 返回订阅提供者的名称，类型为String。
     */
    String name();

    /**
     * 获取订阅主题的模式数组。
     *
     * @return 返回一个String数组，包含订阅的主题模式。
     */
    String[] getTopicPattern();

    /**
     * 根据请求订阅指定的主题。
     *
     * @param request 包含订阅详细信息的请求对象。
     * @return 返回一个Flux对象，用于响应订阅操作的结果。
     */
    Flux<?> subscribe(SubscribeRequest request);

}

