package org.jetlinks.community.utils;

import org.hswebframework.web.authorization.exception.AccessDenyException;
import org.hswebframework.web.exception.NotFoundException;
import reactor.core.publisher.Mono;

/**
 * 异常处理工具
 * 错误处理工具类，提供了一些关于错误处理的静态方法。
 * @author wangzheng
 * @since 1.0
 */
public class ErrorUtils {

    /**
     * 创建一个返回 NotFoundException 的 Mono 错误。
     *
     * @param message 错误信息
     * @param <T> 泛型参数
     * @return 返回一个包含 NotFoundException 错误的 Mono 对象
     */
    public static <T> Mono<T> notFound(String message) {
        return Mono.error(() -> new NotFoundException(message));
    }

    /**
     * 检查给定的异常对象是否包含指定类型的异常。
     *
     * @param e 待检查的异常对象
     * @param target 指定的异常类型数组
     * @return 如果找到指定类型的异常则返回 true，否则返回 false
     */
    @SafeVarargs
    public static boolean hasException(Throwable e, Class<? extends Throwable>... target) {
        Throwable cause = e; // 初始化为传入的异常对象
        while (cause != null) { // 遍历异常链
            for (Class<? extends Throwable> aClass : target) { // 检查每个指定的异常类型
                if (aClass.isInstance(cause)) { // 如果找到匹配的异常类型，则返回 true
                    return true;
                }
            }
            cause = cause.getCause(); // 检查下一个异常对象
        }
        return false; // 如果没有找到匹配的异常类型，则返回 false
    }
}

