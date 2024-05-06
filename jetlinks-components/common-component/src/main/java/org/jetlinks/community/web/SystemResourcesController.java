package org.jetlinks.community.web;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.exception.UnAuthorizedException;
import org.jetlinks.community.resource.Resource;
import org.jetlinks.community.resource.ResourceManager;
import org.jetlinks.community.resource.TypeScriptDeclareResourceProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;

/**
 * 系统资源控制器，负责处理系统资源的相关请求。
 * 通过RESTful API提供资源的查询服务。
 */
@RestController
@RequestMapping("/system/resources")
@Hidden
@AllArgsConstructor
public class SystemResourcesController {

    private final ResourceManager resourceManager; // 资源管理器

    /**
     * 根据资源类型获取资源列表。
     *
     * @param type 资源类型
     * @return 返回一个Flux流，包含指定类型的所有资源的字符串表示
     * @throws Exception 如果没有管理员权限或者获取资源时发生错误，则抛出异常
     */
    @GetMapping("/{type}")
    @SneakyThrows
    public Flux<String> getResources(@PathVariable String type) {
        return Authentication
            .currentReactive()
            .filter(auth -> "admin".equals(auth.getUser().getUsername())) // 仅管理员有权访问
            .switchIfEmpty(Mono.error(UnAuthorizedException::new)) // 无权限时抛出异常
            .flatMapMany(auth -> resourceManager.getResources(type)) // 获取资源
            .map(Resource::asString); // 资源转为字符串
    }

    /**
     * 获取指定ID的TypeScript资源。
     *
     * @param id 资源ID
     * @return 返回一个Mono流，包含指定ID的TypeScript资源的字符串表示，如果不存在则返回空
     * @throws Exception 如果没有访问权限或者获取资源时发生错误，则抛出异常
     */
    @GetMapping("/{id}.d.ts")
    @SneakyThrows
    @Authorize // 需要授权才能访问
    public Mono<String> getTypeScriptResource(@PathVariable String id) {

        return resourceManager
            .getResources(
                TypeScriptDeclareResourceProvider.TYPE, // 指定资源类型
                Collections.singleton(id)) // 指定资源ID集合
            .map(Resource::asString) // 资源转为字符串
            .singleOrEmpty(); // 返回单个资源或空Mono
    }
}
