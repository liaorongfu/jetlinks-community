package org.jetlinks.community.configure.trace;

import org.springframework.http.HttpHeaders;
import reactor.function.Consumer3;

/**
 * 实现了Consumer3接口的HttpHeaderTraceWriter类，用于设置HTTP头部信息。
 */
public class HttpHeaderTraceWriter implements Consumer3<HttpHeaders, String, String> {

    // 实例化一个HttpHeaderTraceWriter对象作为单例
    public static final HttpHeaderTraceWriter INSTANCE = new HttpHeaderTraceWriter();

    /**
     * 接受一个HttpHeaders对象、键和值，将键值对设置到HttpHeaders中。
     *
     * @param headers HttpHeaders对象，代表HTTP请求或响应的头部信息。
     * @param key 要设置的头部字段的键。
     * @param value 头部字段的值。
     */
    @Override
    public void accept(HttpHeaders headers, String key, String value) {
        headers.set(key, value); // 设置HTTP头部字段
    }
}

