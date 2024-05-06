package org.jetlinks.community.resource;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 提供从类路径中的JSON资源的提供者抽象类。实现了ResourceProvider接口。
 */
@Slf4j
public abstract class ClassPathJsonResourceProvider implements ResourceProvider {

    @Getter
    private final String type; // 资源类型
    private final String filePath; // 资源文件路径
    private static final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver(); // 资源解析器
    private List<Resource> cache; // 资源缓存列表

    /**
     * 构造函数。
     * @param type 资源类型。
     * @param filePath 资源文件路径，位于类路径下。
     */
    public ClassPathJsonResourceProvider(String type, String filePath) {
        this.type = type;
        this.filePath = filePath;
    }

    /**
     * 获取所有资源的Flux流。
     * @return 返回资源的Flux流。
     */
    @Override
    public final Flux<Resource> getResources() {
        return Flux.fromIterable(cache == null ? cache = read() : cache);
    }

    /**
     * 根据提供的ID集合获取资源的Flux流。
     * @param id 资源ID的集合。
     * @return 返回匹配给定ID的资源的Flux流。
     */
    @Override
    public final Flux<Resource> getResources(Collection<String> id) {
        Set<String> filter = new HashSet<>(id);
        return getResources()
            .filter(res -> filter.contains(res.getId()));
    }

    /**
     * 从类路径加载JSON资源，并转换为Resource对象列表。
     * @return 返回Resource对象列表。
     */
    private List<Resource> read() {
        List<Resource> resources = new ArrayList<>();
        try {
            log.debug("start load {} resource [{}]", type, filePath);
            for (org.springframework.core.io.Resource resource : resourcePatternResolver.getResources(filePath)) {
                log.debug("loading {} resource {}", type, resource);
                try (InputStream inputStream = resource.getInputStream()) {
                    int index = 0;
                    for (JSONObject json : JSON.parseArray(StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8), JSONObject.class)) {
                        index++;
                        String id = getResourceId(json);
                        if (StringUtils.hasText(id)) {
                            resources.add(SimpleResource.of(id, type, json.toJSONString()));
                        } else {
                            log.warn("{} resource [{}] id (index:{}) is empty : {}", type, resource, index, json);
                        }
                    }
                } catch (Throwable err) {
                    log.debug("load {} resource {} error", type, resource, err);
                }
            }
        } catch (Throwable e) {
            log.warn("load {} resource [{}] error", type, filePath, e);
            return Collections.emptyList();
        }
        return resources;
    }

    /**
     * 从JSON对象中提取资源的ID。
     * @param data JSON对象，代表一个资源。
     * @return 返回资源的ID。
     */
    protected String getResourceId(JSONObject data) {
        return data.getString("id");
    }
}

