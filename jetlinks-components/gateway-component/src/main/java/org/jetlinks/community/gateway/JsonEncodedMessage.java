package org.jetlinks.community.gateway;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import org.jetlinks.rule.engine.executor.PayloadType;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * 代表一个使用JSON编码的消息类，实现了EncodableMessage接口。
 * 这个类允许将任何对象作为原始负载存储，并能够在需要时将其编码为ByteBuf形式的负载。
 */
public class JsonEncodedMessage implements EncodableMessage {

    // 使用volatile修饰，保证多线程下的可见性
    private volatile ByteBuf payload;

    // 使用Object类型存储原始负载，可以是任意类型
    @Getter
    private Object nativePayload;

    /**
     * 构造函数，初始化JsonEncodedMessage实例。
     *
     * @param nativePayload 原始消息负载，不能为空。
     * @throws NullPointerException 如果nativePayload为null，则抛出异常。
     */
    public JsonEncodedMessage(Object nativePayload) {
        Objects.requireNonNull(nativePayload); // 确保nativePayload不为null
        this.nativePayload = nativePayload;
    }

    /**
     * 获取消息的payload，如果尚未编码，则在调用时进行编码。
     *
     * @return 编码后的消息负载的ByteBuf实例。
     */
    @Nonnull
    @Override
    public ByteBuf getPayload() {
        if (payload == null) { // 当payload为空时，进行编码
            payload = PayloadType.JSON.write(nativePayload); // 使用JSON编码类型将nativePayload编码为ByteBuf
        }
        return payload;
    }

}

