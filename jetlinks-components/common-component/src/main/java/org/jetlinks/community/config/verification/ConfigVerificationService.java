package org.jetlinks.community.config.verification;

import io.swagger.v3.oas.annotations.Operation;
import org.hswebframework.web.crud.events.EntitySavedEvent;
import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.config.entity.ConfigEntity;
import org.jetlinks.reactor.ql.utils.CastUtils;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * 配置验证服务
 * @author bestfeng
 */
@RestController
public class ConfigVerificationService {

    // WebClient用于进行HTTP请求
    private final WebClient webClient;

    // 配置验证接口的路径
    private static final String PATH_VERIFICATION_URI = "/system/config/base-path/verification";

    /**
     * 构造函数：初始化WebClient。
     */
    public ConfigVerificationService() {
        this.webClient = WebClient
            .builder()
            .build();
    }

    /**
     * 提供一个接口来验证basePath的配置是否正确。
     *
     * @return Mono<String> 返回一个包含验证信息的字符串，或者在发生错误时返回错误信息。
     */
    @GetMapping(value = PATH_VERIFICATION_URI)
    @Operation(description = "basePath配置验证接口")
    public Mono<String> basePathValidate() {
        return Mono.just("auth:"+PATH_VERIFICATION_URI);
    }

    /**
     * 处理配置保存事件，对保存的配置进行basePath验证。
     *
     * @param event 配置保存事件
     */
    @EventListener
    public void handleConfigSavedEvent(EntitySavedEvent<ConfigEntity> event){
        // 异步进行base-path校验
        event.async(
            Flux.fromIterable(event.getEntity())
                .filter(config -> Objects.equals(config.getScope(), "paths"))
                .flatMap(config-> doBasePathValidate(config.getProperties().get("base-path")))
        );
    }

    /**
     * 对给定的basePath进行验证。
     *
     * @param basePath 待验证的basePath
     * @return Mono<Void> 如果验证成功返回空Mono，否则返回包含错误信息的Mono。
     */
    public Mono<Void> doBasePathValidate(Object basePath) {
        if (basePath == null) {
            return Mono.empty();
        }

        // 创建URI用于后续的HTTP请求验证
        URI uri = URI.create(CastUtils.castString(CastUtils.castString(basePath).concat(PATH_VERIFICATION_URI)));

        // 检查URI的主机是否为禁用的地址
        if (Objects.equals(uri.getHost(), "127.0.0.1") || Objects.equals(uri.getHost(), "localhost")) {
            return Mono.error(new BusinessException("error.base_path_host_error", 500, uri.getHost()));
        }

        // 发起HTTP请求验证basePath的配置是否正确
        return webClient
            .get()
            .uri(uri)
            .exchangeToMono(cr -> {
                if (cr.statusCode().is2xxSuccessful()) {
                    // 检查响应内容是否符合预期
                    return cr.bodyToMono(String.class)
                            .filter(r-> r.contains("auth:"+PATH_VERIFICATION_URI))
                            .switchIfEmpty(Mono.error(()-> new BusinessException("error.base_path_error")));
                }
                return Mono.defer(() -> Mono.error(new BusinessException("error.base_path_error")));
            })
            .timeout(Duration.ofSeconds(3), Mono.error(TimeoutException::new))
            .onErrorResume(err -> {
                // 错误处理，转换不同的异常类型为业务异常
                while (err != null) {
                    if (err instanceof TimeoutException) {
                        return Mono.error(() -> new BusinessException("error.base_path_validate_request_timeout"));
                    } else if (err instanceof UnknownHostException) {
                        return Mono.error(() -> new BusinessException("error.base_path_DNS_resolution_failed"));
                    }
                    err = err.getCause();
                }
                return Mono.error(() -> new BusinessException("error.base_path_error"));
            })
            .then();
    }
}

