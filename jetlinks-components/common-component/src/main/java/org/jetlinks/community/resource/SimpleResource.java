package org.jetlinks.community.resource;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.lang.reflect.Type;

/**
 * 一个简单的资源类，实现了Resource接口。
 * 提供了资源的ID、类型和资源本身的数据封装。
 */
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
@Getter
@Setter
public class SimpleResource implements Resource {
    private String id; // 资源的唯一标识
    private String type; // 资源的类型
    private String resource; // 资源的具体数据，以JSON字符串形式存储

    /**
     * 将资源数据解析成指定类型的对象。
     *
     * @param type 指定的目标类型，是一个Class对象。
     * @param <T> 目标类型的泛型。
     * @return 返回解析后的目标类型对象。
     */
    @Override
    public <T> T as(Class<T> type) {
        return JSON.parseObject(resource, type);
    }

    /**
     * 将资源数据解析成指定类型的对象。
     *
     * @param type 指定的目标类型，是一个Type对象。
     * @param <T> 目标类型的泛型。
     * @return 返回解析后的目标类型对象。
     */
    @Override
    public <T> T as(Type type) {
        return JSON.parseObject(resource, type);
    }

    /**
     * 将资源数据以字符串形式返回。
     *
     * @return 资源数据的字符串形式。
     */
    @Override
    public String asString() {
        return resource;
    }
}

