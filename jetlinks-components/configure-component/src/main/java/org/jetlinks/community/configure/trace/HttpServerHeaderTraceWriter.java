package org.jetlinks.community.configure.trace;

import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.function.Consumer3;

/**
 * 实现了Consumer3接口的HttpServerHeaderTraceWriter类，用于向HTTP服务器响应头中写入跟踪信息。
 */
public class HttpServerHeaderTraceWriter implements Consumer3<ServerHttpRequest.Builder, String, String> {

    // 静态实例，提供单例模式的访问方式
    public static final HttpServerHeaderTraceWriter INSTANCE = new HttpServerHeaderTraceWriter();

    /**
     * 接受一个服务器请求构建器、键和值，将它们作为头信息添加到HTTP响应中。
     *
     * @param builder 服务器请求的构建器，用于修改或构建HTTP请求。
     * @param key 要添加到头中的键。
     * @param value 键对应的值。
     */
    @Override
    public void accept(ServerHttpRequest.Builder builder, String key, String value) {
        // 通过HttpHeaderTraceWriter的实例将键值对添加到请求头
        builder.headers(headers -> HttpHeaderTraceWriter.INSTANCE.accept(headers, key, value));
    }
}

