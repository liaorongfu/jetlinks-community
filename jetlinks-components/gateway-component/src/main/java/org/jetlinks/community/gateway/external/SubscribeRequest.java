package org.jetlinks.community.gateway.external;

import lombok.*;
import org.hswebframework.web.authorization.Authentication;
import org.jetlinks.community.ValueObject;
import org.jetlinks.community.gateway.external.socket.MessagingRequest;

import java.util.Map;

/**
 * 订阅请求类，用于表示一个订阅请求的数据结构。
 * 具有getter、setter、builder、无参构造和全参构造方法。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeRequest implements ValueObject {

    private String id; // 请求唯一标识符

    private String topic; // 订阅的主题

    private Map<String, Object> parameter; // 请求参数，键值对形式

    private Authentication authentication; // 认证信息

    /**
     * 返回请求参数的Map对象。
     * @return 请求参数的Map对象。
     */
    @Override
    public Map<String, Object> values() {
        return parameter;
    }

    /**
     * 根据MessagingRequest和Authentication构建一个SubscribeRequest对象。
     * @param request MessagingRequest对象，包含订阅请求的基本信息。
     * @param authentication Authentication对象，包含认证信息。
     * @return 构建完成的SubscribeRequest对象。
     */
    public static SubscribeRequest of(MessagingRequest request,
                                      Authentication authentication) {
        return SubscribeRequest.builder()
            .id(request.getId())
            .topic(request.getTopic())
            .parameter(request.getParameter())
            .authentication(authentication)
            .build();
    }
}

