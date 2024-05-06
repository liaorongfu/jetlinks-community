package org.jetlinks.community.resource;

import reactor.core.publisher.Flux;

import java.util.Collection;

/**
 * ResourceProvider接口定义了提供资源的方法。
 * 该接口主要负责获取资源的信息，支持根据资源类型和资源ID集合来获取资源。
 */
public interface ResourceProvider {

    /**
     * 获取资源类型的字符串表示。
     *
     * @return 返回资源的类型字符串。
     */
    String getType();

    /**
     * 获取所有资源的Flux流。
     *
     * @return 返回一个包含所有资源的Flux流。
     */
    Flux<Resource> getResources();

    /**
     * 根据提供的资源ID集合获取资源的Flux流。
     *
     * @param id 需要获取的资源的ID集合。
     * @return 返回一个包含指定ID资源的Flux流。
     */
    Flux<Resource> getResources(Collection<String> id);
}

