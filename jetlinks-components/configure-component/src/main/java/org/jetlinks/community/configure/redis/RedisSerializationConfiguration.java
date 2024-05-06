package org.jetlinks.community.configure.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis序列化配置类，该配置类控制Redis模板的序列化方式。
 * 它只在配置属性"spring.redis.serializer"为"obj"时生效，如果该属性不存在，默认也生效。
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.redis", name = "serializer", havingValue = "obj", matchIfMissing = true)
public class RedisSerializationConfiguration {

    /**
     * 创建并配置ReactiveRedisTemplate，用于与Redis进行反应式交互。
     *
     * @param reactiveRedisConnectionFactory 用于创建连接的ReactiveRedisConnectionFactory。
     * @return 配置好的ReactiveRedisTemplate实例，用于键值对的存取操作。
     */
    @Bean
    @Primary
    public ReactiveRedisTemplate<Object, Object> reactiveRedisTemplate(ReactiveRedisConnectionFactory reactiveRedisConnectionFactory) {
        // 初始化ObjectRedisSerializer，用于对象的序列化和反序列化
        ObjectRedisSerializer serializer = new ObjectRedisSerializer();

        // 配置序列化上下文，指定键、值、哈希键、哈希值的序列化方式
        @SuppressWarnings("all")
        RedisSerializationContext<Object, Object> serializationContext = RedisSerializationContext
            .newSerializationContext()
            .key((RedisSerializer) StringRedisSerializer.UTF_8)
            .value(serializer)
            .hashKey(StringRedisSerializer.UTF_8)
            .hashValue(serializer)
            .build();

        // 基于配置的序列化上下文，创建并返回ReactiveRedisTemplate实例
        return new ReactiveRedisTemplate<>(reactiveRedisConnectionFactory, serializationContext);
    }

}

