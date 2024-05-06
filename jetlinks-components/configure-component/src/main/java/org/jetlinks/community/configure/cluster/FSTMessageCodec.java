package org.jetlinks.community.configure.cluster;

import io.netty.util.concurrent.FastThreadLocal;
import io.scalecube.cluster.transport.api.Message;
import io.scalecube.cluster.transport.api.MessageCodec;
import lombok.AllArgsConstructor;
import org.nustaq.serialization.FSTConfiguration;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Supplier;
/**
 * FSTMessageCodec
 * fst，与kryo类似是apache组织的一个开源项目，完全兼容JDK序列化协议的系列化框架，序列化速度大概是JDK的4-10倍，大小是JDK大小的1/3左右
 */
@AllArgsConstructor
public class FSTMessageCodec implements MessageCodec {
    private final FastThreadLocal<FSTConfiguration> configuration;

    public FSTMessageCodec(Supplier<FSTConfiguration> supplier) {

        this(new FastThreadLocal<FSTConfiguration>() {
            @Override
            protected FSTConfiguration initialValue() {
                return supplier.get();
            }
        });
    }

    /**
     * 从给定的输入流中反序列化消息对象。
     * 这个方法会使用FST序列化库来读取输入流中的数据，并将其反序列化为Message对象。
     *
     * @param stream 输入流，包含要被反序列化的消息数据。
     * @return 反序列化后的Message对象。
     * @throws Exception 如果在反序列化过程中发生任何错误，则会抛出异常。
     */
    @Override
    public Message deserialize(InputStream stream) throws Exception {
        // 初始化一个空的消息对象
        Message message = Message.builder().build();
        // 使用配置中获取的FSTObjectInput来读取输入流中的数据
        try (FSTObjectInput input = configuration.get().getObjectInput(stream)) {
            // 将读取的数据反序列化到message对象中
            message.readExternal(input);
        }
        // 返回反序列化后的消息对象
        return message;
    }

    /**
     * 将消息对象序列化到输出流中。
     * 该方法使用FST序列化库将提供的消息对象写入指定的输出流。
     *
     * @param message 需要被序列化的消息对象。该对象应实现Externalizable接口。
     * @param stream 用于接收序列化后消息数据的输出流。
     * @throws Exception 如果序列化过程中发生错误，则抛出异常。
     */
    @Override
    public void serialize(Message message, OutputStream stream) throws Exception {
        // 使用配置获取的FSTObjectOutput实例，安全地尝试序列化消息对象到输出流
        try (FSTObjectOutput output = configuration.get().getObjectOutput(stream)) {
            message.writeExternal(output);
        }
    }
}
