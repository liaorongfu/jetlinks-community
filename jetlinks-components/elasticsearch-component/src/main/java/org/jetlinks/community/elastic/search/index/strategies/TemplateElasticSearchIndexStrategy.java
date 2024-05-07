package org.jetlinks.community.elastic.search.index.strategies;

import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.Version;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.client.indices.GetIndexTemplatesRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexMetadata;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexProperties;
import org.jetlinks.community.elastic.search.service.reactive.ReactiveElasticsearchClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 提供基于模板的Elasticsearch索引策略的抽象类，扩展了AbstractElasticsearchIndexStrategy。
 * 这个类主要处理索引模板的创建和管理，以及索引的保存和搜索。
 */
public abstract class TemplateElasticSearchIndexStrategy extends AbstractElasticSearchIndexStrategy {

    /**
     * 构造函数
     * @param id 策略的唯一标识符
     * @param client 用于与Elasticsearch进行反应式交互的客户端
     * @param properties Elasticsearch索引的配置属性
     */
    public TemplateElasticSearchIndexStrategy(String id, ReactiveElasticsearchClient client, ElasticSearchIndexProperties properties) {
        super(id, client, properties);
    }

    /**
     * 获取索引模板的名称。
     * @param index 原始索引名称
     * @return 模板化的索引名称
     */
    protected String getTemplate(String index) {
        return wrapIndex(index).concat("_template");
    }

    /**
     * 获取索引的别名。
     * @param index 原始索引名称
     * @return 别名化的索引名称
     */
    protected String getAlias(String index) {
        return wrapIndex(index).concat("_alias");
    }

    /**
     * 获取索引模式的列表。
     * @param index 原始索引名称
     * @return 包含单个模式的列表，该模式是原始索引名称的通配符扩展
     */
    protected List<String> getIndexPatterns(String index) {
        return Collections.singletonList(wrapIndex(index).concat("*"));
    }

    /**
     * 获取保存操作应使用的索引名称。此方法必须由子类实现。
     * @param index 原始索引名称
     * @return 保存操作使用的索引名称
     */
    @Override
    public abstract String getIndexForSave(String index);

    /**
     * 获取搜索操作应使用的索引名称。
     * @param index 原始索引名称
     * @return 搜索操作使用的索引别名
     */
    @Override
    public String getIndexForSearch(String index) {
        return getAlias(index);
    }

    /**
     * 创建或更新Elasticsearch索引模板和索引。
     * @param metadata 包含索引元数据的信息
     * @return 完成索引配置的元数据
     */
    @Override
    public Mono<ElasticSearchIndexMetadata> putIndex(ElasticSearchIndexMetadata metadata) {

        // 首先创建索引模板，然后在指定索引上执行额外的操作
        return client
            .putTemplate(createIndexTemplateRequest(metadata))
            // 修改当前索引
            .then(doPutIndex(metadata.newIndexName(getIndexForSave(metadata.getIndex())), true))
            .thenReturn(metadata.newIndexName(wrapIndex(metadata.getIndex())));
    }

    /**
     * 创建索引模板的请求配置。
     * @param metadata 包含索引元数据的信息
     * @return 配置好的PutIndexTemplateRequest对象
     */
    protected PutIndexTemplateRequest createIndexTemplateRequest(ElasticSearchIndexMetadata metadata) {
        String index = wrapIndex(metadata.getIndex());
        PutIndexTemplateRequest request = new PutIndexTemplateRequest(getTemplate(index));
        request.alias(new Alias(getAlias(index)));
        request.settings(properties.toSettings());

        // 配置映射和动态模板
        Map<String, Object> mappingConfig = new HashMap<>();
        mappingConfig.put("properties", createElasticProperties(metadata.getProperties()));
        mappingConfig.put("dynamic_templates", createDynamicTemplates());
        if (client.serverVersion().after(Version.V_7_0_0)) {
            request.mapping(mappingConfig);
        } else {
            request.mapping(Collections.singletonMap("_doc", mappingConfig));
        }

        request.patterns(getIndexPatterns(index));
        return request;
    }

    /**
     * 加载指定索引的元数据。
     * @param index 要加载元数据的索引名称
     * @return 包含索引元数据的Mono对象
     */
    @Override
    public Mono<ElasticSearchIndexMetadata> loadIndexMetadata(String index) {
        // 通过索引模板名称加载元数据
        return client.getTemplate(new GetIndexTemplatesRequest(getTemplate(index)))
                     .filter(resp -> CollectionUtils.isNotEmpty(resp.getIndexTemplates()))
                     .flatMap(resp -> Mono.justOrEmpty(convertMetadata(index, resp
                         .getIndexTemplates()
                         .get(0)
                         .mappings())));
    }
}

