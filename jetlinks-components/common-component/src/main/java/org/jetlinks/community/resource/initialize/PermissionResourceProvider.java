package org.jetlinks.community.resource.initialize;

import com.alibaba.fastjson.JSON;
import org.hswebframework.web.authorization.define.AuthorizeDefinitionInitializedEvent;
import org.hswebframework.web.authorization.define.MergedAuthorizeDefinition;
import org.jetlinks.community.resource.Resource;
import org.jetlinks.community.resource.ResourceProvider;
import org.jetlinks.community.resource.SimpleResource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Collection;

/**
 * 权限资源提供者类，实现了资源提供者接口，用于提供和管理权限资源。
 */
public class PermissionResourceProvider implements ResourceProvider {
    public static final String type = "permission"; // 资源类型为"permission"

    // 合并后的授权定义，用于存储和管理权限资源的定义
    private final MergedAuthorizeDefinition definition = new MergedAuthorizeDefinition();

    /**
     * 获取资源提供者的类型。
     *
     * @return 返回资源提供者的类型，为"permission"。
     */
    @Override
    public String getType() {
        return type;
    }

    /**
     * 获取所有资源。
     *
     * @return 返回一个资源流，其中包含所有权限资源。
     */
    @Override
    public Flux<Resource> getResources() {
        return Flux
            .fromIterable(definition.getResources()) // 从合并后的授权定义中获取资源
            .map(def -> SimpleResource.of(def.getId(), getType(), JSON.toJSONString(def))); // 将授权定义转换为简单资源对象
    }

    /**
     * 根据提供的ID集合获取资源。
     *
     * @param id 资源ID的集合。
     * @return 返回一个资源流，包含所有匹配的资源，如果ID集合为null，则返回所有资源。
     */
    @Override
    public Flux<Resource> getResources(Collection<String> id) {
        return getResources()
            .filter(resource -> id == null || id.contains(resource.getId())); // 过滤出匹配的资源
    }

    /**
     * 处理授权定义初始化事件，用于更新合并后的授权定义。
     *
     * @param event 授权定义初始化事件。
     */
    @EventListener
    public void handleInitializedEvent(AuthorizeDefinitionInitializedEvent event) {
        definition.merge(event.getAllDefinition()); // 合并事件中提供的所有授权定义
    }
}

