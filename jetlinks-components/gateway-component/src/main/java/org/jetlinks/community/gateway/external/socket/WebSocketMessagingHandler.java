package org.jetlinks.community.gateway.external.socket;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.ReactiveAuthenticationManager;
import org.hswebframework.web.authorization.token.UserToken;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.hswebframework.web.logger.ReactiveLogger;
import org.jetlinks.community.gateway.external.Message;
import org.jetlinks.community.gateway.external.MessagingManager;
import org.jetlinks.community.gateway.external.SubscribeRequest;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket消息处理类，负责处理WebSocket连接的各种消息。
 */
@AllArgsConstructor
@Slf4j
public class WebSocketMessagingHandler implements WebSocketHandler {

    private final MessagingManager messagingManager; // 消息管理器
    private final UserTokenManager userTokenManager; // 用户令牌管理器
    private final ReactiveAuthenticationManager authenticationManager; // 反应式认证管理器

    /**
     * 处理WebSocket会话。
     *
     * 此方法负责处理客户端通过WebSocket发送的消息，包括认证、心跳、订阅和取消订阅等操作。
     *
     * @param session WebSocket会话，用于接收和发送消息，以及获取会话相关信息。
     * @return 返回一个Mono<Void>，表示处理完成。当处理完成后，此Mono会完成，表示会话处理结束。
     */
    @Override
    @Nonnull
    public Mono<Void> handle(@Nonnull WebSocketSession session) {
        // 解析WebSocket请求路径中的token
        String[] path = session.getHandshakeInfo().getUri().getPath().split("[/]");
        if (path.length == 0) {
            // 如果无法解析出token，发送错误消息并关闭连接
            return session.send(Mono.just(session.textMessage(JSON.toJSONString(
                Message.error("auth", null, "错误的请求")
            )))).then(session.close(CloseStatus.BAD_DATA));
        }
        String token = path[path.length - 1];

        // 用于存储订阅信息的并发映射
        Map<String, Disposable> subs = new ConcurrentHashMap<>();

        // 验证用户token，并处理消息
        return userTokenManager.getByToken(token)
                               .map(UserToken::getUserId)
                               .flatMap(authenticationManager::getByUserId)
                               .switchIfEmpty(session
                                                  .send(Mono.just(session.textMessage(JSON.toJSONString(
                                                      Message.authError()
                                                  ))))
                                                  .then(session.close(CloseStatus.BAD_DATA))
                                                  .then(Mono.empty()))
                               .flatMap(auth -> session
                                   .receive() // 接收客户端消息
                                   .doOnNext(message -> {
                                       try {
                                           // 处理心跳和ping消息
                                           if (message.getType() == WebSocketMessage.Type.PONG) {
                                               return;
                                           }
                                           if (message.getType() == WebSocketMessage.Type.PING) {
                                               session
                                                   .send(Mono.just(session.pongMessage(DataBufferFactory::allocateBuffer)))
                                                   .subscribe();
                                               return;
                                           }
                                           // 解析并处理其他类型的消息
                                           MessagingRequest request = JSON.parseObject(message.getPayloadAsText(), MessagingRequest.class);
                                           if (request == null) {
                                               return;
                                           }
                                           // 处理ping请求
                                           if (request.getType() == MessagingRequest.Type.ping) {
                                               session
                                                   .send(Mono.just(session.textMessage(JSON.toJSONString(
                                                       Message.pong(request.getId())
                                                   ))))
                                                   .subscribe();
                                               return;
                                           }
                                           // 校验消息id是否为空
                                           if (StringUtils.isEmpty(request.getId())) {
                                               session
                                                   .send(Mono.just(session.textMessage(JSON.toJSONString(
                                                       Message.error(request.getType().name(), null, "id不能为空")
                                                   )))).subscribe();
                                               return;
                                           }
                                           // 处理订阅和取消订阅请求
                                           if (request.getType() == MessagingRequest.Type.sub) {
                                               // 检查是否重复订阅
                                               Disposable old = subs.get(request.getId());
                                               if (old != null && !old.isDisposed()) {
                                                   return;
                                               }
                                               // 执行订阅操作
                                               Map<String, String> context = new HashMap<>();
                                               context.put("userId", auth.getUser().getId());
                                               context.put("userName", auth.getUser().getName());
                                               Disposable sub = messagingManager
                                                   .subscribe(SubscribeRequest.of(request, auth))
                                                   .doOnEach(ReactiveLogger.onError(err -> log.error("{}", err.getMessage(), err)))
                                                   .onErrorResume(err -> Mono.just(Message.error(request.getId(), request.getTopic(), err)))
                                                   .map(msg -> session.textMessage(JSON.toJSONString(msg)))
                                                   .doOnComplete(() -> {
                                                       log.debug("complete subscription:{}", request.getTopic());
                                                       subs.remove(request.getId());
                                                       Mono.just(session.textMessage(JSON.toJSONString(Message.complete(request.getId()))))
                                                           .as(session::send)
                                                           .subscribe();
                                                   })
                                                   .doOnCancel(() -> {
                                                       log.debug("cancel subscription:{}", request.getTopic());
                                                       subs.remove(request.getId());
                                                   })
                                                   .transform(session::send)
                                                   .subscriberContext(ReactiveLogger.start(context))
                                                   .subscriberContext(Context.of(Authentication.class, auth))
                                                   .subscribe();
                                               if (!sub.isDisposed()) {
                                                   subs.put(request.getId(), sub);
                                               }
                                           } else if (request.getType() == MessagingRequest.Type.unsub) {
                                               Optional.ofNullable(subs.remove(request.getId()))
                                                       .ifPresent(Disposable::dispose);
                                           } else {
                                               // 处理不支持的消息类型
                                               session.send(Mono.just(session.textMessage(JSON.toJSONString(
                                                   Message.error(request.getId(), request.getTopic(), "不支持的类型:" + request.getType())
                                               )))).subscribe();
                                           }
                                       } catch (Exception e) {
                                           // 处理异常，发送错误消息
                                           log.warn(e.getMessage(), e);
                                           session.send(Mono.just(session.textMessage(JSON.toJSONString(
                                               Message.error("illegal_argument", null, "消息格式错误")
                                           )))).subscribe();
                                       }
                                   })
                                   .then())
                               .doFinally(r -> {
                                   // 最终确保清理所有订阅
                                   subs.values().forEach(Disposable::dispose);
                                   subs.clear();
                               });

    }

}

