package org.jetlinks.community.device.message.transparent;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 提供透明消息编码解码器的接口。
 * 该接口的实现负责提供特定于提供商的消息编码解码器实例。
 */
public interface TransparentMessageCodecProvider {

    /**
     * 获取提供商的标识。
     *
     * @return 返回提供商的名称或标识符。
     */
    String getProvider();

    /**
     * 根据配置创建并返回一个透明消息编码解码器。
     *
     * @param configuration 配置参数映射，用于初始化编码解码器。
     * @return 返回一个Mono实例，该实例包含一个透明消息编码解码器。
     */
    Mono<TransparentMessageCodec> createCodec(Map<String,Object> configuration);

}

