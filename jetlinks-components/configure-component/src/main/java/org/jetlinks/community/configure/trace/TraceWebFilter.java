package org.jetlinks.community.configure.trace;

import org.jetlinks.core.trace.MonoTracer;
import org.jetlinks.core.trace.TraceHolder;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 一个实现WebFilter和Ordered接口的过滤器类，用于在HTTP请求处理过程中进行跟踪。
 */
public class TraceWebFilter implements WebFilter, Ordered {

    /**
     * 处理HTTP请求，进行跟踪信息的添加和传递。
     *
     * @param exchange 服务器web交换机，提供请求和响应的交互接口。
     * @param chain web过滤器链，用于将请求传递给下一个过滤器或处理程序。
     * @return Mono<Void> 表示异步处理完成的信号。
     */
    @SuppressWarnings("all")
    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             WebFilterChain chain) {
        // 生成跟踪名称，格式为 /http/method/path
        String spanName = "/http/"+exchange.getRequest().getMethodValue()  + exchange.getRequest().getPath().value();

        // 创建请求的副本，用于在其中添加跟踪信息
        ServerHttpRequest.Builder requestCopy = exchange
            .getRequest()
            .mutate();

        // 将追踪信息写入响应头，以便客户端可以接收
        return TraceHolder
            // 将追踪上下文写入请求头，以便下游服务可以读取
            .writeContextTo(exchange.getResponse().getHeaders(), HttpHeaderTraceWriter.INSTANCE)
            // 将追踪上下文写入请求头，以便上游服务可以读取
            .then(TraceHolder.writeContextTo(requestCopy, HttpServerHeaderTraceWriter.INSTANCE))
            // 执行过滤器链，即处理请求并传递给下一个过滤器
            .then(Mono.defer(() -> chain.filter(exchange.mutate().request(requestCopy.build()).build())))
            // 创建跟踪span，用于跟踪当前请求的生命周期
            .as(MonoTracer.create(spanName))
            // 从请求头中提取并追加上级跟踪信息
            .as(MonoTracer.createWith(exchange.getRequest().getHeaders(),HttpHeadersGetter.INSTANCE));
    }

    /**
     * 获取过滤器的执行顺序。
     *
     * @return int 过滤器的执行优先级。值越小，优先级越高。
     */
    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE + 100;
    }
}

