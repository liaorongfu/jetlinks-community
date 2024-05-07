package org.jetlinks.community.gateway.external;

import reactor.core.publisher.Flux;

/**
 * MessagingManager接口定义了消息管理的功能，提供了订阅消息的方法。
 */
public interface MessagingManager {

    /**
     * 根据订阅请求订阅消息，并返回一个Flux流，该流可以异步地提供订阅到的消息。
     *
     * @param request 订阅请求，包含了订阅的详细信息。
     * @return 返回一个Flux<Message>，它是一个异步序列，会按顺序提供订阅到的消息。
     */
    Flux<Message> subscribe(SubscribeRequest request);

}

