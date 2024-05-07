package org.jetlinks.community.gateway.external.socket;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * MessagingRequest 类用于表示消息传递的请求。
 * 它包含了请求的基本信息，如请求ID，请求类型，主题以及相关的参数。
 */
@Getter
@Setter
public class MessagingRequest {

    private String id; // 请求的唯一标识符

    private Type type; // 请求的类型，包括发布、订阅、取消订阅和ping

    private String topic; // 请求相关的话题

    private Map<String,Object> parameter; // 请求携带的参数，以键值对形式存储


    /**
     * Type 枚举定义了消息请求的四种类型：发布(pub)、订阅(sub)、取消订阅(unsub)和ping。
     */
    public enum Type{
        pub,sub,unsub,ping
    }
}

