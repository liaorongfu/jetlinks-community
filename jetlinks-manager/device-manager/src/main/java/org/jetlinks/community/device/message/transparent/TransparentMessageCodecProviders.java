package org.jetlinks.community.device.message.transparent;

import org.hswebframework.web.exception.I18nSupportException;
import org.jctools.maps.NonBlockingHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 透明消息编码解码器提供者容器类。
 * 该类用于管理并提供透明消息编码解码器的提供者实例。
 */
public class TransparentMessageCodecProviders {

    /**
     * 存储所有注册的编码解码器提供者的映射。
     * 使用NonBlockingHashMap以支持高并发访问。
     */
    public static Map<String, TransparentMessageCodecProvider> providers = new NonBlockingHashMap<>();

    /**
     * 添加一个编码解码器提供者到容器中。
     *
     * @param provider 要添加的编码解码器提供者实例。
     */
    static void addProvider(TransparentMessageCodecProvider provider) {
        providers.put(provider.getProvider(), provider);
    }

    /**
     * 获取所有注册的编码解码器提供者列表。
     *
     * @return 所有提供者的列表。
     */
    public static List<TransparentMessageCodecProvider> getProviders() {
        return new ArrayList<>(providers.values());
    }

    /**
     * 根据提供者名称获取对应的编码解码器提供者。
     *
     * @param provider 提供者名称。
     * @return 对应的编码解码器提供者，如果不存在则返回空。
     */
    public static Optional<TransparentMessageCodecProvider> getProvider(String provider) {
        return Optional.ofNullable(providers.get(provider));
    }

    /**
     * 根据提供者名称获取对应的编码解码器提供者。
     * 如果提供者不存在，则抛出I18nSupportException异常。
     *
     * @param provider 提供者名称。
     * @return 对应的编码解码器提供者。
     * @throws I18nSupportException 如果提供者不存在。
     */
    public static TransparentMessageCodecProvider getProviderNow(String provider) {
        return getProvider(provider)
            .orElseThrow(()->new I18nSupportException("error.unsupported_codec",provider));
    }
}

