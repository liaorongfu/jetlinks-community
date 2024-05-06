package org.jetlinks.community.codec;

import io.netty.util.concurrent.FastThreadLocal;
import lombok.SneakyThrows;
import org.nustaq.serialization.FSTConfiguration;

import java.io.*;

/**
 * 提供多种序列化器的工具类。
 */
public class Serializers {

    // JDK内置序列化器，使用Java标准的ObjectInputStream和ObjectOutputStream进行序列化和反序列化。
    private static final ObjectSerializer JDK = new ObjectSerializer() {
        @Override
        @SneakyThrows
        public ObjectInput createInput(InputStream stream) {
            return new ObjectInputStream(stream);
        }

        @Override
        @SneakyThrows
        public ObjectOutput createOutput(OutputStream stream) {
            return new ObjectOutputStream(stream);
        }
    };

    // FST序列化器，是一个高性能的Java对象序列化库。
    private static final ObjectSerializer FST = new ObjectSerializer() {

        // FST序列化器的配置，使用FastThreadLocal保证每个线程都有自己的配置实例，以避免并发问题。
        final FastThreadLocal<FSTConfiguration> conf =
            new FastThreadLocal<FSTConfiguration>() {
                @Override
                protected FSTConfiguration initialValue() {
                    FSTConfiguration configuration = FSTConfiguration.createDefaultConfiguration();
                    configuration.setForceSerializable(true);
                    configuration.setClassLoader(FST.getClass().getClassLoader());
                    return configuration;
                }
            };

        @Override
        @SneakyThrows
        public ObjectInput createInput(InputStream stream) {
            return conf.get().getObjectInput(stream);
        }

        @Override
        @SneakyThrows
        public ObjectOutput createOutput(OutputStream stream) {
            return conf.get().getObjectOutput(stream);
        }
    };

    // 默认序列化器，根据系统属性"jetlinks.object.serializer.type"的值决定使用FST或JDK序列化器。
    private static final ObjectSerializer DEFAULT;

    static {
        DEFAULT = System.getProperty("jetlinks.object.serializer.type", "fst").equals("fst") ? FST : JDK;
    }

    /**
     * 获取JDK序列化器。
     *
     * @return JDK序列化器实例。
     */
    public static ObjectSerializer jdk() {
        return JDK;
    }

    /**
     * 获取FST序列化器。
     *
     * @return FST序列化器实例。
     */
    public static ObjectSerializer fst() {
        return FST;
    }

    /**
     * 获取默认序列化器。
     *
     * @return 默认序列化器实例，根据系统属性决定是FST还是JDK序列化器。
     */
    public static ObjectSerializer getDefault() {
        return DEFAULT;
    }


}

