package org.jetlinks.community.resource;

import java.lang.reflect.Type;

/**
 * Resource接口定义了资源的基本操作。
 * 它提供了获取资源ID、资源类型，以及将资源转换为指定类型或字符串的方法。
 */
public interface Resource {

    /**
     * 获取资源的唯一标识符。
     *
     * @return 资源的唯一标识符，类型为String。
     */
    String getId();

    /**
     * 获取资源的类型。
     *
     * @return 资源的类型，类型为String。
     */
    String getType();

    /**
     * 将资源转换为指定的Java类型。
     *
     * @param type 指定的Java类型，类型为Class<T>。
     * @return 转换后的对象，类型为T。
     * @param <T> 指定的转换类型。
     */
    <T> T as(Class<T> type);

    /**
     * 将资源转换为指定的泛型类型。
     *
     * @param type 指定的泛型类型，类型为Type。
     * @return 转换后的对象，类型为T。
     * @param <T> 指定的转换类型。
     */
    <T> T as(Type type);

    /**
     * 将资源转换为字符串表示。
     *
     * @return 资源的字符串表示。
     */
    String asString();
}

