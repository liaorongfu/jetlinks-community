package org.jetlinks.community.device.message.transparent.script;

import lombok.RequiredArgsConstructor;
import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.community.device.message.transparent.SimpleTransparentMessageCodec;
import org.jetlinks.community.device.message.transparent.TransparentMessageCodec;
import org.jetlinks.community.device.message.transparent.TransparentMessageCodecProvider;
import org.jetlinks.community.script.Script;
import org.jetlinks.community.script.ScriptFactory;
import org.jetlinks.community.script.Scripts;
import org.jetlinks.community.script.context.ExecutionContext;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

/**
 * 提供JSR223脚本编解码器的实现，用于透明消息编解码。
 */
@Component
public class Jsr223TransparentMessageCodecProvider implements TransparentMessageCodecProvider {

    /**
     * 返回编解码器提供商的名称。
     *
     * @return 编解码器提供商的名称。
     */
    @Override
    public String getProvider() {
        return "jsr223";
    }

    /**
     * 根据配置创建编解码器。
     *
     * @param configuration 配置信息，包括脚本语言和脚本内容。
     * @return 编解码器的Mono实例。
     */
    @Override
    public Mono<TransparentMessageCodec> createCodec(Map<String, Object> configuration) {
        // 默认语言为JavaScript
        String lang = (String) configuration.getOrDefault("lang", "js");
        String script = (String) configuration.get("script");
        // 确保语言和脚本不为空
        Assert.hasText(lang, "lang can not be null");
        Assert.hasText(script, "script can not be null");

        // 根据语言获取脚本工厂
        ScriptFactory factory = Scripts.getFactory(lang);

        // 创建编解码器上下文
        CodecContext context = new CodecContext(factory);

        // 尝试绑定脚本到编解码操作
        SimpleTransparentMessageCodec.Codec codec = factory.bind(
            Script.of("jsr223-transparent", script),
            SimpleTransparentMessageCodec.Codec.class,
            ExecutionContext.create(Collections.singletonMap("codec", context)));

        // 如果没有编码器，但有codec，设置编码器
        if (context.encoder == null && codec != null) {
            context.onDownstream(codec::encode);
        }
        // 如果没有解码器，但有codec，设置解码器
        if (context.decoder == null && codec != null) {
            context.onUpstream(codec::decode);
        }

        // 如果codec和编解码器都未设置，返回错误
        if (codec == null && context.encoder == null && context.decoder == null) {
            return Mono.error(new ValidationException("script", "error.codec_message_undefined"));
        }

        // 返回编解码器Mono实例
        return Mono
            .deferContextual(ctx -> Mono
                .just(
                    new SimpleTransparentMessageCodec(context)
                ));
    }

    /**
     * 编解码器上下文，封装了脚本工厂和编解码函数。
     */
    @RequiredArgsConstructor
    public static class CodecContext implements SimpleTransparentMessageCodec.Codec {
        private final ScriptFactory factory;
        private Function<SimpleTransparentMessageCodec.EncodeContext, Object> encoder;
        private Function<SimpleTransparentMessageCodec.DecodeContext, Object> decoder;

        /**
         * 设置下游（发送）编解码器。
         *
         * @param encoder 下游编解码器。
         */
        public void onDownstream(Function<SimpleTransparentMessageCodec.EncodeContext, Object> encoder) {
            this.encoder = encoder;
        }

        /**
         * 设置上游（接收）编解码器。
         *
         * @param decoder 上游编解码器。
         */
        public void onUpstream(Function<SimpleTransparentMessageCodec.DecodeContext, Object> decoder) {
            this.decoder = decoder;
        }

        /**
         * 解码消息。
         *
         * @param context 解码上下文。
         * @return 解码后的对象。
         */
        @Override
        public Object decode(SimpleTransparentMessageCodec.DecodeContext context) {
            if (decoder == null) {
                return null;
            }
            return factory.convertToJavaType(decoder.apply(context));
        }

        /**
         * 编码消息。
         *
         * @param context 编码上下文。
         * @return 编码后的对象。
         */
        @Override
        public Object encode(SimpleTransparentMessageCodec.EncodeContext context) {
            if (encoder == null) {
                return null;
            }
            return factory.convertToJavaType(encoder.apply(context));
        }

    }

}

