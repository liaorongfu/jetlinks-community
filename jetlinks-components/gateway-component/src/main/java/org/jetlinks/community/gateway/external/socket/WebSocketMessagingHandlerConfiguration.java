package org.jetlinks.community.gateway.external.socket;

import org.hswebframework.web.authorization.ReactiveAuthenticationManager;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.jetlinks.community.gateway.external.MessagingManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

@Configuration
//@ConditionalOnBean({
//    ReactiveAuthenticationManager.class,
//    UserTokenManager.class
//})
public class WebSocketMessagingHandlerConfiguration {


    /**
     * 创建并配置WebSocket消息处理的映射。
     *
     * @param messagingManager 用于管理消息的bean。
     * @param userTokenManager 用于管理用户令牌的bean。
     * @param authenticationManager 用于认证的bean。
     * @return 配置好的HandlerMapping，将特定的URL模式映射到WebSocket消息处理程序。
     */
    @Bean
    public HandlerMapping webSocketMessagingHandlerMapping(MessagingManager messagingManager,
                                           UserTokenManager userTokenManager,
                                           ReactiveAuthenticationManager authenticationManager) {

        // 创建WebSocket消息处理程序，注入依赖项
        WebSocketMessagingHandler messagingHandler=new WebSocketMessagingHandler(
            messagingManager,
            userTokenManager,
            authenticationManager
        );

        // 初始化并配置URL到WebSocketHandler的映射
        final Map<String, WebSocketHandler> map = new HashMap<>(1);
        map.put("/messaging/**", messagingHandler);

        // 创建并配置SimpleUrlHandlerMapping，设置优先级并映射URL
        final SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE); // 设置高优先级
        mapping.setUrlMap(map); // 设置映射关系
        return mapping;
    }


    /**
     * 创建WebSocketHandlerAdapter Bean.
     * 这个方法配置了一个WebSocketHandlerAdapter的实例，它负责处理WebSocket的逻辑。
     *
     * @return WebSocketHandlerAdapter 新的WebSocketHandlerAdapter实例
     * @ConditionalOnMissingBean 注解表明，只有当当前环境中不存在任何WebSocketHandlerAdapter Bean时，这个方法才会被调用，从而创建一个新的Bean实例。
     */
    @Bean
    @ConditionalOnMissingBean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }


}
