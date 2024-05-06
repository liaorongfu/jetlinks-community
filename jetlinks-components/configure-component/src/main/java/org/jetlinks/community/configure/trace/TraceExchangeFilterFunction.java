package org.jetlinks.community.configure.trace;

import org.jetlinks.core.trace.TraceHolder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;

/**
 * 一个用于添加追踪信息到HTTP请求的ExchangeFilterFunction实现。
 */
public final class TraceExchangeFilterFunction implements ExchangeFilterFunction {

    // 单例实例，用于提供TraceExchangeFilterFunction的全局唯一实例
    private static final TraceExchangeFilterFunction INSTANCE = new TraceExchangeFilterFunction();

    /**
     * 提供TraceExchangeFilterFunction的全局唯一实例。
     *
     * @return TraceExchangeFilterFunction的单例实例。
     */
    public static ExchangeFilterFunction instance() {
        return INSTANCE;
    }

    // 私有构造函数，防止外部实例化
    private TraceExchangeFilterFunction() {
    }

    /**
     * 在HTTP请求中添加追踪信息。
     *
     * @param request 客户端请求，将从中读取信息并添加追踪头。
     * @param next 下一个过滤器或终端操作的函数。
     * @return 包含客户端响应的Mono对象。
     */
    @Override
    @Nonnull
    public Mono<ClientResponse> filter(@Nonnull ClientRequest request,
                                       @Nonnull ExchangeFunction next) {
        // 将追踪上下文信息写入到请求头中
        return TraceHolder
            .writeContextTo(ClientRequest.from(request), ClientRequest.Builder::header)
            .flatMap(builder -> next.exchange(builder.build()));
    }

}

