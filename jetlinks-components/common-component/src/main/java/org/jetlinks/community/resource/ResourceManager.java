package org.jetlinks.community.resource;

import reactor.core.publisher.Flux;

import java.util.Collection;

/**
 * 资源管理器接口，提供获取资源的方法。
 */
public interface ResourceManager {

    /**
     * 根据资源类型获取资源的Flux流。
     *
     * @param type 资源的类型，用于筛选资源。
     * @return 返回一个Flux流，包含所有匹配的资源对象。
     */
    Flux<Resource> getResources(String type);

    /**
     * 根据资源类型和一组资源ID获取资源的Flux流。
     *
     * @param type 资源的类型，用于筛选资源。
     * @param id   一个资源ID的集合，用于进一步筛选资源。
     * @return 返回一个Flux流，包含所有匹配的资源对象。
     */
    Flux<Resource> getResources(String type, Collection<String> id);

}

