package org.jetlinks.community.resource;

import org.apache.commons.lang.StringUtils;
import org.jetlinks.core.utils.TypeScriptUtils;
import reactor.core.publisher.Flux;

import java.util.Collection;

/**
 * TypeScript声明资源提供者类，实现了资源提供者接口。
 * 用于提供TypeScript声明文件的资源。
 */
public class TypeScriptDeclareResourceProvider implements ResourceProvider {

    public static String TYPE = "typescript-declare"; // 资源类型标识

    /**
     * 获取资源提供者的类型。
     *
     * @return 返回资源提供者的类型，为字符串形式。
     */
    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * 获取所有资源。
     *
     * @return 返回一个空的Flux流，表示没有资源。
     */
    @Override
    public Flux<Resource> getResources() {
        return Flux.empty();
    }

    /**
     * 根据提供的ID集合获取对应的资源。
     *
     * @param ids 资源的ID集合。
     * @return 返回一个Flux流，包含根据ID加载的资源，如果ID对应的资源不存在或加载失败，则该ID的资源为空。
     */
    @Override
    public Flux<Resource> getResources(Collection<String> ids) {

        return Flux
            .fromIterable(ids) // 将ID集合转为Flux流
            .mapNotNull(id -> {
                try {
                    // 尝试加载指定ID的TypeScript声明文件
                    String resource = TypeScriptUtils.loadDeclare(id);
                    if (StringUtils.isEmpty(resource)) {
                        // 如果加载的内容为空，则返回null
                        return null;
                    }
                    // 创建并返回资源对象
                    return SimpleResource.of(
                        id,
                        getType(),
                        resource
                    );
                } catch (Throwable err) {
                    // 加载失败返回null
                    return null;
                }
            });
    }
}

