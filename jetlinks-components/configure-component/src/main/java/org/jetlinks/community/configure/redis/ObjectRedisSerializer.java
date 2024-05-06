package org.jetlinks.community.configure.redis;

import io.netty.util.concurrent.FastThreadLocal;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.utils.SerializeUtils;
import org.jetlinks.community.codec.Serializers;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * 用于Redis序列化的对象类，实现了RedisSerializer接口。
 * 使用Lombok的@Slf4j注解进行日志记录，@AllArgsConstructor注解生成全参数构造函数。
 */
@Slf4j
@AllArgsConstructor
public class ObjectRedisSerializer implements RedisSerializer<Object> {

    // 使用FastThreadLocal为每个线程提供一个重用的ByteArrayOutputStream实例，降低内存分配开销
    static final FastThreadLocal<ByteArrayOutputStream> STREAM_LOCAL = new FastThreadLocal<ByteArrayOutputStream>() {
        @Override
        protected ByteArrayOutputStream initialValue() {
            // 初始化时返回一个ByteArrayOutputStream实例，容量为1024字节
            // 重写close方法，避免关闭流，而是重置流的状态
            return new ByteArrayOutputStream(1024) {
                @Override
                public void close() {
                    reset();
                }
            };
        }
    };

    /**
     * 序列化对象为字节数组。
     * @param o 需要被序列化的对象。
     * @return 序列化后的字节数组。如果对象为null，则返回null。
     * @throws SerializationException 序列化过程中发生的任何异常。
     */
    @Override
    @SneakyThrows
    public byte[] serialize(Object o) throws SerializationException {
        if (o == null) {
            return null;
        }
        ByteArrayOutputStream arr = STREAM_LOCAL.get(); // 获取当前线程的ByteArrayOutputStream实例
        try (ObjectOutput output = Serializers.getDefault().createOutput(arr)) {
            // 使用SerializeUtils将对象写入到输出流中
            SerializeUtils.writeObject(o, output);
            output.flush(); // 刷新输出流，确保所有数据都被写入

            return arr.toByteArray(); // 返回序列化后的字节数组
        } catch (Throwable e) {
            log.error(e.getMessage(), e); // 记录异常信息
            throw e; // 重新抛出异常
        }
    }

    /**
     * 将字节数组反序列化为对象。
     * @param bytes 需要被反序列化的字节数组。
     * @return 反序列化后的对象。如果字节数组为null，则返回null。
     * @throws SerializationException 反序列化过程中发生的任何异常。
     */
    @Override
    @SneakyThrows
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null) {
            return null;
        }
        try (ObjectInput input = Serializers
            .getDefault()
            .createInput(new ByteArrayInputStream(bytes))) {
            // 使用SerializeUtils从输入流中读取对象
            return SerializeUtils.readObject(input);
        } catch (Throwable e) {
            log.error(e.getMessage(), e); // 记录异常信息
            throw e; // 重新抛出异常
        }
    }
}

