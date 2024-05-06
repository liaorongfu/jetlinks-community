package org.jetlinks.community.resource;

import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认资源管理器类，实现了ResourceManager接口，用于管理资源提供者和获取资源。
 */
public class DefaultResourceManager implements ResourceManager {
    // 使用ConcurrentHashMap来存储资源提供者，以支持线程安全的读写操作。
    private final Map<String, ResourceProvider> providers = new ConcurrentHashMap<>();

    /**
     * 构造函数，初始化资源管理器。
     */
    public DefaultResourceManager() {
    }

    /**
     * 添加一个资源提供者。
     *
     * @param provider 要添加的资源提供者，不能为null。
     */
    public void addProvider(ResourceProvider provider) {
        providers.put(provider.getType(), provider);
    }

    /**
     * 根据资源类型获取资源的Flux流。
     *
     * @param type 资源类型，不能为null。
     * @return 返回一个Flux流，包含指定类型的资源。
     */
    @Override
    public Flux<Resource> getResources(String type) {
        return getResources(type, null);
    }

    /**
     * 根据资源类型和ID集合获取资源的Flux流。
     *
     * @param type 资源类型，不能为null。
     * @param id 资源ID的集合，可以为null。
     * @return 返回一个Flux流，包含指定类型的资源，如果指定了ID集合，则只包含那些ID的资源。
     */
    @Override
    public Flux<Resource> getResources(String type, Collection<String> id) {
        // 根据类型获取资源提供者，然后根据是否提供了ID来获取相应的资源。
        return this
            .getProvider(type)
            .flatMapMany(provider -> CollectionUtils.isEmpty(id) ? provider.getResources() : provider.getResources(id));
    }

    /**
     * 根据资源类型获取对应的资源提供者。
     *
     * @param type 资源类型，不能为null。
     * @return 返回一个Mono流，包含指定类型的资源提供者，如果不存在，则返回空Mono。
     */
    private Mono<ResourceProvider> getProvider(String type) {
        return Mono.justOrEmpty(providers.get(type));
    }

}

