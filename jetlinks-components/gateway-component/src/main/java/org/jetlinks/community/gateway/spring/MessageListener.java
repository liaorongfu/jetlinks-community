package org.jetlinks.community.gateway.spring;

import org.jetlinks.core.event.TopicPayload;
import reactor.core.publisher.Mono;

/**
 * MessageListener 接口定义了消息监听的方法。
 * 该接口期望实现者能够处理特定的消息类型，并在消息接收到时执行相应的逻辑。
 *
 * @param <T> TopicPayload 的泛型类型，代表消息的具体类型。
 */
public interface MessageListener {

    /**
     * 当接收到消息时调用的方法。
     *
     * @param message 接收到的消息内容，类型为 TopicPayload 的泛型实例。
     *                包含了消息的主题和其他可能的负载信息。
     * @return Mono<Void> 异步操作，表示消息处理的完成。当消息处理完成后，
     *         该异步操作将会完成，不返回任何结果。
     */
    Mono<Void> onMessage(TopicPayload message);

}

