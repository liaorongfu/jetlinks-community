package org.jetlinks.community;

import lombok.Generated;
import org.jetlinks.core.config.ConfigKey;
import org.jetlinks.core.message.HeaderKey;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * @author wangzheng
 * @since 1.0
 */
public interface PropertyConstants {
    // 组织ID键
    Key<String> orgId = Key.of("orgId");

    // 设备名称键
    Key<String> deviceName = Key.of("deviceName");
    // 产品名称键
    Key<String> productName = Key.of("productName");

    // 产品ID键
    Key<String> productId = Key.of("productId");
    // 用户ID键
    Key<String> uid = Key.of("_uid");
    // 设备创建者ID键
    Key<String> creatorId = Key.of("creatorId");

    // 设备接入网关ID键
    Key<String> accessId = Key.of("accessId");

    /**
     * 设备接入方式键
     * 该接入方式定义了设备是如何通过网关接入的。
     * 参见: org.jetlinks.community.gateway.supports.DeviceGatewayProvider#getId
     */
    Key<String> accessProvider = Key.of("accessProvider");

    /**
     * 从配置映射中获取值
     * @param key 配置键
     * @param map 配置映射
     * @param <T> 值的类型
     * @return 返回配置值的Optional包装，如果不存在则返回空Optional
     */
    @SuppressWarnings("all")
    static <T> Optional<T> getFromMap(ConfigKey<T> key, Map<String, Object> map) {
        return Optional.ofNullable((T) map.get(key.getKey()));
    }

    /**
     * 配置键接口，继承自ConfigKey和HeaderKey
     */
    @Generated
    interface Key<V> extends ConfigKey<V>, HeaderKey<V> {

        @Override
        default Type getValueType() {
            return ConfigKey.super.getValueType();
        }

        @Override
        default Class<V> getType() {
            return ConfigKey.super.getType();
        }

        /**
         * 创建一个具有指定键的Key实例，默认值为null。
         * @param key 配置键
         * @param <T> 值的类型
         * @return 返回Key实例
         */
        static <T> Key<T> of(String key) {
            return new Key<T>() {
                @Override
                public String getKey() {
                    return key;
                }

                @Override
                public T getDefaultValue() {
                    return null;
                }
            };
        }

        /**
         * 创建一个具有指定键和默认值的Key实例。
         * @param key 配置键
         * @param defaultValue 默认值
         * @param <T> 值的类型
         * @return 返回Key实例
         */
        static <T> Key<T> of(String key, T defaultValue) {
            return new Key<T>() {
                @Override
                public String getKey() {
                    return key;
                }

                @Override
                public T getDefaultValue() {
                    return defaultValue;
                }
            };
        }

        /**
         * 创建一个具有指定键和默认值生成器的Key实例。
         * @param key 配置键
         * @param defaultValue 默认值生成器
         * @param <T> 值的类型
         * @return 返回Key实例
         */
        static <T> Key<T> of(String key, Supplier<T> defaultValue) {
            return new Key<T>() {
                @Override
                public String getKey() {
                    return key;
                }

                @Override
                public T getDefaultValue() {
                    return defaultValue.get();
                }
            };
        }

        /**
         * 创建一个具有指定键、默认值生成器和值类型的Key实例。
         * @param key 配置键
         * @param defaultValue 默认值生成器
         * @param type 值类型
         * @param <T> 值的类型
         * @return 返回Key实例
         */
        static <T> Key<T> of(String key, Supplier<T> defaultValue, Type type) {
            return new Key<T>() {
                @Override
                public Type getValueType() {
                    return type;
                }

                @Override
                public String getKey() {
                    return key;
                }

                @Override
                public T getDefaultValue() {
                    return defaultValue.get();
                }
            };
        }

    }
}

