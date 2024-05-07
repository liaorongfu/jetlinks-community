package org.jetlinks.community.elastic.search.index.strategies;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.Version;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.elasticsearch.common.compress.CompressedXContent;
import org.jetlinks.core.metadata.DataType;
import org.jetlinks.core.metadata.PropertyMetadata;
import org.jetlinks.core.metadata.SimplePropertyMetadata;
import org.jetlinks.core.metadata.types.*;
import org.jetlinks.community.elastic.search.enums.ElasticDateFormat;
import org.jetlinks.community.elastic.search.enums.ElasticPropertyType;
import org.jetlinks.community.elastic.search.index.DefaultElasticSearchIndexMetadata;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexMetadata;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexProperties;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexStrategy;
import org.jetlinks.community.elastic.search.service.reactive.ReactiveElasticsearchClient;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;
/**
 * 抽象类定义了Elasticsearch索引策略，提供了一系列方法来操作Elasticsearch索引，包括创建索引、更新映射等。
 * 这个类是抽象的，需要被具体实现。
 */
@AllArgsConstructor
@Slf4j
public abstract class AbstractElasticSearchIndexStrategy implements ElasticSearchIndexStrategy {
    @Getter
    private final String id; // 索引策略的唯一标识

    protected ReactiveElasticsearchClient client; // Elasticsearch的响应式客户端

    protected ElasticSearchIndexProperties properties; // 索引属性配置

    /**
     * 将索引名称转换为小写。
     * @param index 原始索引名称
     * @return 转换为小写的索引名称
     */
    protected String wrapIndex(String index) {
        return index.toLowerCase();
    }

    /**
     * 检查指定的索引是否存在。
     * @param index 索引名称
     * @return 返回一个Mono<Boolean>，表示索引是否存在
     */
    protected Mono<Boolean> indexExists(String index) {
        return client.existsIndex(new GetIndexRequest(wrapIndex(index)));
    }

    /**
     * 创建一个新的索引。
     * @param metadata 索引元数据
     * @return 返回一个空的Mono<Void>
     */
    protected Mono<Void> doCreateIndex(ElasticSearchIndexMetadata metadata) {
        return client
            .createIndex(createIndexRequest(metadata))
            .then();
    }
    /**
     * 更新索引的映射，如果索引已存在，则只更新映射；如果不存在且justUpdateMapping为false，则创建索引。
     * @param metadata 索引元数据
     * @param justUpdateMapping 是否仅更新映射，不创建索引
     * @return 返回一个空的Mono<Void>
     */
    protected Mono<Void> doPutIndex(ElasticSearchIndexMetadata metadata,
                                    boolean justUpdateMapping) {
        String index = wrapIndex(metadata.getIndex());
        return this.indexExists(index)
                   .flatMap(exists -> {
                       if (exists) {
                           return doLoadIndexMetadata(index)
                               .flatMap(oldMapping -> Mono.justOrEmpty(createPutMappingRequest(metadata, oldMapping)))
                               .flatMap(request -> client.putMapping(request))
                               .then();
                       }
                       if (justUpdateMapping) {
                           return Mono.empty();
                       }
                       return doCreateIndex(metadata);
                   });
    }
    /**
     * 加载指定索引的元数据。
     * @param _index 索引名称
     * @return 返回一个包含索引元数据的Mono<ElasticSearchIndexMetadata>
     */
    protected Mono<ElasticSearchIndexMetadata> doLoadIndexMetadata(String _index) {
        String index = wrapIndex(_index);
        return client.getMapping(new GetMappingsRequest().indices(index))
                     .flatMap(resp -> Mono.justOrEmpty(convertMetadata(index, resp.mappings().get(index))));
    }

    /**
     * 创建索引请求。
     * @param metadata 索引元数据
     * @return 返回一个CreateIndexRequest对象
     */
    protected CreateIndexRequest createIndexRequest(ElasticSearchIndexMetadata metadata) {
        CreateIndexRequest request = new CreateIndexRequest(wrapIndex(metadata.getIndex()));
        request.settings(properties.toSettings());
        Map<String, Object> mappingConfig = new HashMap<>();
        mappingConfig.put("properties", createElasticProperties(metadata.getProperties()));
        mappingConfig.put("dynamic_templates", createDynamicTemplates());
        if (client.serverVersion().after(Version.V_7_0_0)) {
            request.mapping(mappingConfig);
        } else {
            request.mapping(Collections.singletonMap("_doc", mappingConfig));
        }
        return request;
    }
    /**
     * 创建更新映射请求。
     * @param metadata 索引的新元数据
     * @param ignore 要忽略的旧元数据
     * @return 返回一个PutMappingRequest对象或空Mono，如果不需要更新映射则返回空Mono
     */
    private PutMappingRequest createPutMappingRequest(ElasticSearchIndexMetadata metadata, ElasticSearchIndexMetadata ignore) {
        Map<String, Object> properties = createElasticProperties(metadata.getProperties());
        Map<String, Object> ignoreProperties = createElasticProperties(ignore.getProperties());
        for (Map.Entry<String, Object> en : ignoreProperties.entrySet()) {
            log.trace("ignore update index [{}] mapping property:{},{}", wrapIndex(metadata.getIndex()), en.getKey(), en
                .getValue());
            properties.remove(en.getKey());
        }
        if (properties.isEmpty()) {
            log.debug("ignore update index [{}] mapping", wrapIndex(metadata.getIndex()));
            return null;
        }
        Map<String, Object> mappingConfig = new HashMap<>();
        PutMappingRequest request = new PutMappingRequest(wrapIndex(metadata.getIndex()));
        List<PropertyMetadata> allProperties = new ArrayList<>();
        allProperties.addAll(metadata.getProperties());
        allProperties.addAll(ignore.getProperties());

        mappingConfig.put("properties", createElasticProperties(allProperties));
        request.source(mappingConfig);
        return request;
    }
    /**
     * 根据属性列表创建Elasticsearch属性映射。
     * @param metadata 属性元数据列表
     * @return 返回一个包含属性映射的Map
     */
    protected Map<String, Object> createElasticProperties(List<PropertyMetadata> metadata) {
        if (metadata == null) {
            return new HashMap<>();
        }
        return metadata
            .stream()
            .collect(Collectors.toMap(PropertyMetadata::getId,
                                      prop -> this.createElasticProperty(prop.getValueType()), (a, v) -> a));
    }
    /**
     * 根据数据类型创建单个属性的Elasticsearch映射。
     * @param type 属性的数据类型
     * @return 返回一个包含属性映射的Map
     */
    protected Map<String, Object> createElasticProperty(DataType type) {
        Map<String, Object> property = new HashMap<>();
        if (type instanceof DateTimeType) {
            property.put("type", "date");
            property.put("format", ElasticDateFormat.getFormat(
                ElasticDateFormat.epoch_millis,
                ElasticDateFormat.strict_date_hour_minute_second,
                ElasticDateFormat.strict_date_time,
                ElasticDateFormat.strict_date)
            );
        } else if (type instanceof DoubleType) {
            property.put("type", "double");
        } else if (type instanceof LongType) {
            property.put("type", "long");
        } else if (type instanceof IntType) {
            property.put("type", "integer");
        } else if (type instanceof FloatType) {
            property.put("type", "float");
        } else if (type instanceof BooleanType) {
            property.put("type", "boolean");
        } else if (type instanceof GeoType) {
            property.put("type", "geo_point");
        } else if (type instanceof GeoShapeType) {
            property.put("type", "geo_shape");
        } else if (type instanceof ArrayType) {
            ArrayType arrayType = ((ArrayType) type);
            return createElasticProperty(arrayType.getElementType());
        } else if (type instanceof ObjectType) {
            property.put("type", "nested");
            ObjectType objectType = ((ObjectType) type);
            if (!CollectionUtils.isEmpty(objectType.getProperties())) {
                property.put("properties", createElasticProperties(objectType.getProperties()));
            }
        } else {
            property.put("type", "keyword");
            property.put("ignore_above", 512);
        }
        return property;
    }
    /**
     * 将Elasticsearch的映射元数据转换为我们的索引元数据模型。
     * @param index 索引名称
     * @param metadata Elasticsearch的映射元数据
     * @return 返回一个ElasticSearchIndexMetadata对象
     */
    protected ElasticSearchIndexMetadata convertMetadata(String index, MappingMetadata metadata) {
        MappingMetadata mappingMetadata;
        Object properties = null;
        Map<String, Object> metaData = metadata.getSourceAsMap();

        if (metaData.containsKey("properties")) {
            Object res = metaData.get("properties");
            if (res instanceof Map) {
                properties = res;
            } else if (res instanceof MappingMetadata) {
                mappingMetadata = ((MappingMetadata) res);
                properties = mappingMetadata.sourceAsMap();
            } else if (res instanceof CompressedXContent) {
                mappingMetadata = new MappingMetadata(((CompressedXContent) res));
                properties = mappingMetadata.sourceAsMap();
            } else {
                throw new UnsupportedOperationException("unsupported index metadata" + metaData);
            }

        } else {
            Object res = metaData.get("_doc");
            if (res instanceof MappingMetadata) {
                mappingMetadata = ((MappingMetadata) res);
            } else if (res instanceof CompressedXContent) {
                mappingMetadata = new MappingMetadata(((CompressedXContent) res));
            } else {
                throw new UnsupportedOperationException("unsupported index metadata" + metaData);
            }
            properties = mappingMetadata.getSourceAsMap().get("properties");
        }
        if (properties == null) {
            throw new UnsupportedOperationException("unsupported index metadata" + metaData);
        }

        return new DefaultElasticSearchIndexMetadata(index, convertProperties(properties));
    }
    /**
     * 转换属性为我们的属性元数据列表。
     * @param properties Elasticsearch映射中的属性
     * @return 返回一个属性元数据列表
     */
    @SuppressWarnings("all")
    protected List<PropertyMetadata> convertProperties(Object properties) {
        if (properties == null) {
            return new ArrayList<>();
        }
        return ((Map<String, Map<String, Object>>) properties)
            .entrySet()
            .stream()
            .map(entry -> convertProperty(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }

    /**
     * 将给定的属性信息转换为属性元数据。
     *
     * @param property 属性名，也是将要设置给元数据的ID和名称。
     * @param map 包含属性类型和其他可能的配置信息的映射。
     * @return 转换后的属性元数据，包含了属性的ID、名称和数据类型。
     */
    private PropertyMetadata convertProperty(String property, Map<String, Object> map) {
        // 获取属性类型
        String type = String.valueOf(map.get("type"));
        // 初始化简单属性元数据
        SimplePropertyMetadata metadata = new SimplePropertyMetadata();
        metadata.setId(property);
        metadata.setName(property);
        // 尝试根据类型获取Elasticsearch属性类型
        ElasticPropertyType elasticPropertyType = ElasticPropertyType.of(type);
        if (null != elasticPropertyType) {
            // 获取对应的数据类型
            DataType dataType = elasticPropertyType.getType();
            // 处理对象类型或嵌套类型，并设置其属性
            if ((elasticPropertyType == ElasticPropertyType.OBJECT
                || elasticPropertyType == ElasticPropertyType.NESTED)
                && dataType instanceof ObjectType) {
                @SuppressWarnings("all")
                Map<String, Object> nestProperties = (Map<String, Object>) map.get("properties");
                if (null != nestProperties) {
                    // 如果是对象类型，设置其属性
                    ObjectType objectType = ((ObjectType) dataType);
                    objectType.setProperties(convertProperties(nestProperties));
                }
            }
            // 设置属性元数据的数据类型
            metadata.setValueType(dataType);
        } else {
            // 如果无法识别类型，设置为全局字符串类型
            metadata.setValueType(StringType.GLOBAL);
        }
        return metadata;
    }

    /**
     * 创建动态模板的列表。
     * 动态模板允许为Elasticsearch中的不同数据类型定义灵活的映射规则。
     * 该方法当前为字符串类型和日期类型各创建了一个动态模板。
     *
     * @return 返回包含动态模板配置的列表。每个动态模板都以Map形式表示，其中包含映射规则和类型配置。
     */
    protected List<?> createDynamicTemplates() {
        List<Map<String, Object>> maps = new ArrayList<>();
        // 创建适用于字符串类型的动态模板配置
        {
            Map<String, Object> config = new HashMap<>();
            config.put("match_mapping_type", "string"); // 指定匹配的映射类型为字符串
            config.put("mapping", createElasticProperty(StringType.GLOBAL)); // 设置字符串类型的映射属性
            maps.add(Collections.singletonMap("string_fields", config)); // 将配置添加到列表中
        }
        // 创建适用于日期类型的动态模板配置
        {
            Map<String, Object> config = new HashMap<>();
            config.put("match_mapping_type", "date"); // 指定匹配的映射类型为日期
            config.put("mapping", createElasticProperty(DateTimeType.GLOBAL)); // 设置日期类型的映射属性
            maps.add(Collections.singletonMap("date_fields", config)); // 将配置添加到列表中
        }

        return maps;
    }
}
