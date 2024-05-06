package org.jetlinks.community.configure.crud;

import io.netty.util.concurrent.FastThreadLocal;
import lombok.SneakyThrows;
import org.hswebframework.ezorm.core.ApacheCommonPropertyOperator;
import org.hswebframework.ezorm.core.GlobalConfig;
import org.hswebframework.ezorm.core.ObjectPropertyOperator;
import org.hswebframework.web.bean.FastBeanCopier;
import org.hswebframework.web.bean.SingleValueMap;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static org.hswebframework.web.bean.FastBeanCopier.include;
/**
 * FastObjectPropertyOperator类实现了ObjectPropertyOperator接口，
 * 用于快速操作对象的属性。
 */
@Component
public class FastObjectPropertyOperator implements ObjectPropertyOperator {
    /**
     * 静态初始化块，用于在类加载时初始化全局配置。
     * 这段代码设置了一个新的属性操作器：FastObjectPropertyOperator。
     * 它是全局配置的一部分，影响着所有使用GlobalConfig的属性操作。
     *
     * 无参数。
     * 无返回值。
     */
    static {
        GlobalConfig.setPropertyOperator(new FastObjectPropertyOperator());
    }

    /**
     * 定义一个线程本地缓存，用于存储单值映射对象。这个缓存是线程安全的，每个线程都有自己的缓存实例。
     * 使用 {@link FastThreadLocal} 来实现线程隔离的缓存，确保每个线程操作的缓存不会互相影响。
     */
    static final FastThreadLocal<SingleValueMap<Object, Object>> cache = new FastThreadLocal<SingleValueMap<Object, Object>>() {
        /**
         * 初始化方法，当线程第一次访问缓存时调用此方法，保证每个线程都有一个初始的、空的 {@link SingleValueMap} 对象作为缓存。
         * @return 返回一个新的、空的 {@link SingleValueMap} 实例，用于该线程的缓存。
         */
        @Override
        protected SingleValueMap<Object, Object> initialValue() {
            return new SingleValueMap<>();
        }
    };

    /**
     * 获取一个SingleValueMap对象。如果缓存中的SingleValueMap对象为空，则直接返回该对象；
     * 如果不为空，则返回一个新的SingleValueMap对象。
     *
     * @return SingleValueMap<Object, Object> 返回的SingleValueMap对象，可能为空。
     */
    private static SingleValueMap<Object, Object> take() {
        // 从缓存中获取SingleValueMap对象
        SingleValueMap<Object, Object> map = cache.get();
        // 如果获取的map为空，则直接返回，否则返回一个新的SingleValueMap对象
        if (map.isEmpty()) {
            return map;
        }
        return new SingleValueMap<>();
    }

    /**
     * 获取给定源对象中指定属性的值。
     * 该方法首先检查属性键是否包含"."或"["，如果是，则使用Apache Commons的属性操作符来处理。
     * 如果源对象是一个Map，直接从Map中获取对应键的值。
     * 如果源对象不是Map，将会尝试将源对象的属性值复制到一个临时的SingleValueMap中，然后从该临时Map中获取值。
     *
     * @param source 源对象，从中获取属性值。
     * @param key 要获取的属性的键，可以是嵌套属性或数组属性的路径。
     * @return 返回一个Optional，包含属性的值，如果属性不存在或值为null，则返回空的Optional。
     */
    @Override
    public Optional<Object> getProperty(Object source, String key) {
        // 检查属性键是否为复杂键（包含"."或"["），是则使用特殊处理逻辑
        if (key.contains(".") || key.contains("[")) {
            return ApacheCommonPropertyOperator.INSTANCE.getProperty(source, key);
        }
        // 如果源对象是Map，直接从Map中获取值
        if (source instanceof Map) {
            return Optional.ofNullable(((Map<?, ?>) source).get(key));
        } else {
            // 对于非Map类型的源对象，将其属性复制到临时Map中，然后获取值
            SingleValueMap<Object, Object> map = take();
            try {
                // 复制源对象的指定属性到临时Map
                FastBeanCopier.copy(source, map, include(key));
                Object value = map.getValue();
                return Optional.ofNullable(value);
            } finally {
                // 清理临时Map
                map.clear();
            }
        }
    }


    /**
     * 设置给定对象的属性值。
     * 该方法首先检查属性名是否包含"."、"["，或者值是否为null。如果是，则使用ApacheCommonPropertyOperator进行设置。
     * 如果对象是Map类型，直接将属性名和值作为键值对放入Map中。
     * 否则，使用SingleValueMap临时存储属性名和值，然后通过FastBeanCopier将该临时Map的内容复制到对象中，最后清空临时Map。
     *
     * @param object 要设置属性的目标对象。
     * @param name 属性名。
     * @param value 属性值。
     */
    @Override
    @SuppressWarnings("all")
    public void setProperty(Object object, String name, Object value) {
        // 当属性名包含特殊字符或值为null时，使用ApacheCommonPropertyOperator进行处理
        if (name.contains(".") || name.contains("[") || value == null) {
            ApacheCommonPropertyOperator.INSTANCE.setProperty(object, name, value);
            return;
        }
        // 如果对象是Map类型，直接添加键值对
        if (object instanceof Map) {
            ((Map<String, Object>) object).put(name, value);
            return;
        }
        // 使用SingleValueMap作为中间容器存储属性值，然后复制到目标对象上
        SingleValueMap<Object, Object> map = take();
        try {
            map.put(name, value);
            FastBeanCopier.copy(map, object);
        } finally {
            // 操作完成后清空SingleValueMap
            map.clear();
        }

    }

    /**
     * 尝试获取对象的属性值，如果该属性不存在或值为null，则创建一个新的属性值并设置到该对象上。
     *
     * @param object 要获取属性的对象。
     * @param name 属性的名称。
     * @return 属性的值。如果属性不存在、无法确定属性类型或创建实例失败，则返回null。
     */
    @Override
    @SneakyThrows
    public Object getPropertyOrNew(Object object, String name) {
        // 尝试获取属性当前的值，如果不存在则为null
        Object value = getProperty(object, name).orElse(null);
        if (null == value) {
            // 尝试获取属性的类型，如果无法确定类型则返回null
            Class<?> clazz = getPropertyType(object, name).orElse(null);
            if (null == clazz) {
                return null;
            }
            // 创建属性类型的实例并设置到对象上
            value = clazz.getConstructor().newInstance();
            setProperty(object, name, value);
            // 由于设置新的值可能会被copy,所以重新获取值以确保拿到最新设置的值
            value = getProperty(object, name).orElse(null);
        }
        return value;
    }

    /**
     * 获取指定对象上指定属性的类型。
     *
     * @param object 要查询属性的对象，不能为null。
     * @param name 属性的名称，不能为null或空字符串。
     * @return 如果找到对应的属性类型，则返回包含该类型的Optional，否则返回空Optional。
     *         通过调用super类的getPropertyType方法来实现属性类型的获取。
     */
    @Override
    public Optional<Class<?>> getPropertyType(Object object, String name) {
        return ObjectPropertyOperator.super.getPropertyType(object, name);
    }

}
