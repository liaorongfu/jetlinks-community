package org.jetlinks.community.configure.trace;

import io.opentelemetry.context.propagation.TextMapGetter;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.MultiValueMap;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 实现了TextMapGetter接口，用于从MultiValueMap中获取HTTP头信息。
 */
public class HttpHeadersGetter implements TextMapGetter<MultiValueMap<String, String>> {

    // HttpHeadersGetter的单例实例
    public static final HttpHeadersGetter INSTANCE = new HttpHeadersGetter();

    /**
     * 返回MultiValueMap中所有的键。
     *
     * @param map MultiValueMap对象，不可为null。
     * @return 返回一个包含所有键的集合。
     */
    @Override
    public Iterable<String> keys(MultiValueMap<String, String> map) {
        return map.keySet();
    }

    /**
     * 根据键从MultiValueMap中获取对应的值。
     *
     * @param map MultiValueMap对象，可能为null。
     * @param key 要获取的键，不可为null。
     * @return 如果找到键对应的值，则返回最后一个值；如果没有找到，或者map为null，则返回null。
     */
    @Nullable
    @Override
    public String get(@Nullable MultiValueMap<String, String> map, String key) {
        if (map == null) {
            return null;
        }
        List<String> code = map.get(key);

        // 如果键对应的值列表为空，返回null；否则返回列表中的最后一个元素。
        return CollectionUtils.isEmpty(code) ? null : code.get(code.size() - 1);
    }
}

