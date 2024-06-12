package org.jetlinks.community.elastic.search.utils;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.hswebframework.web.exception.BusinessException;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
/**
 * ReactorActionListener 类用于将 ActionListener 的回调模式转换为 Reactor 的 Mono 流。
 * 这使得异步操作的结果可以更方便地融入 Reactor 的流处理中。
 */
public class ReactorActionListener {

    /**
     * 创建一个 Mono 流，该流在 ActionListener 接口的 onResponse 或 onFailure 方法被调用时产生值。
     *
     * @param listenerConsumer 接收 ActionListener 的消费者，用于触发 Mono 流的值生成。
     * @param onSuccess 在操作成功时应用的函数，将结果转换为 Mono<R>。
     * @param onError 在操作失败时应用的函数，将异常转换为 Mono<R>。
     * @param <R> Mono 流产生的最终结果类型。
     * @param <T> ActionListener 处理的中间结果类型。
     * @return 一个 Mono 流，根据操作的成功或失败产生相应的结果。
     */
    public static <R, T> Mono<R> mono(Consumer<ActionListener<T>> listenerConsumer,
                                      Function<T, Mono<R>> onSuccess,
                                      Function<Exception, Mono<R>> onError) {
        return Mono.<Mono<R>>create(sink -> {
            listenerConsumer.accept(new ActionListener<T>() {
                @Override
                public void onResponse(T t) {
                    try {
                        sink.success(onSuccess.apply(t));
                    } catch (Exception e) {
                        sink.error(e);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    try {
                        sink.success(onError.apply(e));
                    } catch (Exception e2) {
                        sink.error(e2);
                    }
                }
            });

        }).flatMap(Function.identity())
            .onErrorResume(ElasticsearchStatusException.class, e -> {
                // 特别处理 ElasticsearchStatusException，对于 404 错误不抛出异常，而是返回空 Mono。
                if (e.status().getStatus() == 404) {
                    if(e.getMessage().contains("index_not_found_exception")){
                        log.debug("{}",e.getMessage());
                    }else{
                        log.warn("{}",e.getDetailedMessage(),e);
                    }
                    return Mono.empty();
                }
                // 对于其他异常，转换为 BusinessException 后抛出。
                return Mono.error(new BusinessException(e.getMessage(), e));
            });
    }

    /**
     * 创建一个Mono流，该流在给定的ActionListener的onResponse方法被调用时产生值。
     * 这是一个重载方法，用于在不需要处理失败情况的场景下，从ActionListener转换为Mono流。
     *
     * @param listenerConsumer 接收ActionListener的消费者，用于触发Mono流的值生成。
     * @param onSuccess 将ActionListener的成功结果转换为Mono流的函数。
     * @param <R> Mono流产生的最终结果类型。
     * @param <T> ActionListener处理的中间结果类型。
     * @return 一个Mono流，它在ActionListener的成功回调中产生值。
     */
    public static <R, T> Mono<R> mono(Consumer<ActionListener<T>> listenerConsumer,
                                      Function<T, Mono<R>> onSuccess) {
        // 通过调用重载方法，将监听器和成功处理函数传递给mono方法，错误处理使用默认的Mono::error策略。
        return mono(listenerConsumer, onSuccess, Mono::error);
    }

    /**
     * 创建一个Mono流，该流从提供的ActionListener中获取结果。
     * 当ActionListener的onResponse方法被调用时，Mono流将发出给定的响应值。
     * 如果发生错误，Mono流将根据提供的错误处理策略发出错误。
     *
     * @param listenerConsumer 一个消费者，用于设置将结果提供给ActionListener的回调。
     * @param <R> Mono流预期产生的结果类型。
     * @return 一个Mono流，它在ActionListener的回调被调用时产生结果。
     */
    public static <R> Mono<R> mono(Consumer<ActionListener<R>> listenerConsumer) {
        // 通过调用重载的mono方法，使用默认的响应和错误处理策略创建Mono流。
        return mono(listenerConsumer, Mono::justOrEmpty, Mono::error);
    }
}
