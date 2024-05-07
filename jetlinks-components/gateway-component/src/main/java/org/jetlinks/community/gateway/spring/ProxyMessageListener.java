package org.jetlinks.community.gateway.spring;

import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.bean.FastBeanCopier;
import org.hswebframework.web.proxy.Proxy;
import org.jetlinks.core.NativePayload;
import org.jetlinks.core.Payload;
import org.jetlinks.core.codec.Codecs;
import org.jetlinks.core.codec.Decoder;
import org.jetlinks.core.event.TopicPayload;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.StringJoiner;
import java.util.function.BiFunction;

/**
 * 代理消息监听器，用于动态生成针对特定目标对象和方法的消息监听处理。
 */
@Slf4j
class ProxyMessageListener implements MessageListener {
    private final Class<?> paramType; // 方法参数类型
    private final Object target; // 目标对象
    private final ResolvableType resolvableType; // 可解析的参数类型，支持泛型处理

    private final Method method; // 目标方法
    private final BiFunction<Object, Object, Object> proxy; // 动态生成的代理，用于调用目标方法

    private volatile Decoder<?> decoder; // 解码器，用于解析消息负载

    /**
     * 构造函数，初始化监听器。
     *
     * @param target 目标对象，监听器会调用其方法。
     * @param method 目标方法，监听器将动态生成代理来调用此方法。
     */
    @SuppressWarnings("all")
    ProxyMessageListener(Object target, Method method) {
        this.target = target;
        this.method = method;
        Class<?>[] parameterTypes = method.getParameterTypes();
        // 支持无参或单参方法，多参数方法不受支持
        if (parameterTypes.length > 1) {
            throw new UnsupportedOperationException("unsupported method [" + method + "] parameter");
        }
        if (parameterTypes.length == 1) {
            paramType = parameterTypes[0];
        } else {
            paramType = Void.class;
        }

        Class<?> targetType = ClassUtils.getUserClass(target);

        // 动态生成调用目标方法的代码
        StringJoiner code = new StringJoiner("\n");
        code.add("public Object apply(Object target,Object param){");
        code.add(targetType.getName() + " _target = (" + targetType.getName() + ")target;");

        String invokeCode;
        if (paramType != Void.class) {
            code.add(paramType.getName() + " _param = (" + paramType.getName() + ")param;");
            invokeCode = " _target." + method.getName() + "(_param);";
        } else {
            invokeCode = " _target." + method.getName() + "();";
        }
        // 根据方法返回类型决定是否需要返回值
        if (method.getReturnType() != Void.TYPE) {
            code.add("return " + invokeCode);
        } else {
            code.add(invokeCode)
                .add("return null;");
        }

        code.add("}");
        this.resolvableType = ResolvableType.forMethodParameter(method, 0, targetType);
        this.proxy = Proxy.create(BiFunction.class)
            .addMethod(code.toString())
            .newInstance();

    }

    /**
     * 转换消息，确保其参数类型与目标方法匹配。
     *
     * @param message 消息主题负载。它包含需要被转换的消息内容。
     * @return 转换后的消息参数，类型匹配目标方法的参数。这个转换过程确保了消息可以被正确地传递给目标方法执行。
     */
    Object convert(TopicPayload message) {
        // 检查消息类型是否已经是期望的参数类型，如果是，则无需转换直接返回
        if (Payload.class.isAssignableFrom(paramType)) {
            return message;
        }
        try {
            Payload payload = message.getPayload();
            Object decodedPayload;
            // 根据负载类型处理原始负载或解码负载
            if (payload instanceof NativePayload) {
                // 如果负载是原生类型，直接获取其原生对象
                decodedPayload = ((NativePayload<?>) payload).getNativeObject();
            } else {
                // 如果负载需要解码，使用解码器进行解码
                if (decoder == null) {
                    // 如果解码器尚未初始化，则查找并初始化解码器
                    decoder = Codecs.lookup(resolvableType);
                }
                // 使用解码器解码负载
                decodedPayload = decoder.decode(message);
            }
            // 检查解码后的负载是否已经是期望的参数类型，如果是，则直接返回
            if (paramType.isInstance(decodedPayload)) {
                return decodedPayload;
            }
            // 如果解码后的负载类型不匹配，尝试使用快速Bean复制器进行类型转换
            return FastBeanCopier.DEFAULT_CONVERT.convert(decodedPayload, paramType, resolvableType.resolveGenerics());
        } finally {
            // 无论转换成功与否，最后都确保消息被释放，避免资源泄露
            message.release();
        }
    }


    /**
     * 消息监听器的主要入口点，处理接收到的消息。
     *
     * @param message 消息主题负载。该参数包含了接收到的消息内容。
     * @return 返回一个Mono<Void>表示操作完成。Mono<Void>表示一个异步操作，该操作不返回任何结果。
     */
    @Override
    public Mono<Void> onMessage(TopicPayload message) {
        try {
            boolean paramVoid = paramType == Void.class; // 判断方法参数是否为Void类型，用于后续处理消息转换。
            try {
                // 根据消息类型转换消息，并通过代理调用目标方法。
                Object val = proxy.apply(target, paramVoid ? null : convert(message));

                // 如果目标方法返回的是一个Publisher类型，则将其转换为Mono然后继续处理。
                if (val instanceof Publisher) {
                    return Mono.from((Publisher<?>) val).then();
                }

                // 如果目标方法返回非Publisher类型，或者不返回任何值，则默认返回空的Mono。
                return Mono.empty();
            } finally {
                // 如果方法参数为Void类型，确保消息被释放，以避免资源泄露。
                if (paramVoid) {
                    message.release();
                }
            }
        } catch (Throwable e) {
            // 记录调用监听器方法时发生的任何错误，确保异常被处理，避免程序中断。
            log.error("invoke event listener [{}] error", toString(), e);
        }
        // 在任何情况下，最终都返回一个空的Mono。
        return Mono.empty();
    }


    /**
     * 返回此监听器的字符串表示形式，格式为"目标类名.目标方法名"。
     *
     * @return 字符串表示形式。
     */
    @Override
    public String toString() {
        return ClassUtils.getUserClass(target).getSimpleName() + "." + method.getName();
    }
}

