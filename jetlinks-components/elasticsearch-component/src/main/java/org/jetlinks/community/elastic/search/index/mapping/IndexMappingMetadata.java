package org.jetlinks.community.elastic.search.index.mapping;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.community.elastic.search.enums.ElasticPropertyType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用于存储索引映射元数据的类。
 * @author bsetfeng
 * @since 1.0
 **/
@Getter
@Setter
public class IndexMappingMetadata {

    private String index; // 索引名称

    // 存储字段名到其对应单一映射元数据的映射
    private Map<String, SingleMappingMetadata> metadata = new HashMap<>();

    /**
     * 获取所有映射元数据列表。
     *
     * @return 所有字段的单一映射元数据列表。
     */
    public List<SingleMappingMetadata> getAllMetaData() {
        return metadata.entrySet()
                .stream()
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /**
     * 根据字段名获取映射元数据。
     *
     * @param fieldName 字段名。
     * @return 对应字段的单一映射元数据，如果不存在则为null。
     */
    public SingleMappingMetadata getMetaData(String fieldName) {
        return metadata.get(fieldName);
    }

    /**
     * 根据属性类型获取映射元数据列表。
     *
     * @param type 属性类型。
     * @return 匹配给定属性类型的单一映射元数据列表。
     */
    public List<SingleMappingMetadata> getMetaDataByType(ElasticPropertyType type) {
        return getAllMetaData()
                .stream()
                .filter(singleMapping -> singleMapping.getType().equals(type))
                .collect(Collectors.toList());
    }

    /**
     * 根据属性类型获取映射元数据的映射表。
     *
     * @param type 属性类型。
     * @return 以字段名为键，单一映射元数据为值的映射表。
     */
    public Map<String, SingleMappingMetadata> getMetaDataByTypeToMap(ElasticPropertyType type) {
        return getMetaDataByType(type)
                .stream()
                .collect(Collectors.toMap(SingleMappingMetadata::getName, Function.identity()));
    }

    /**
     * 添加或更新映射元数据。
     *
     * @param singleMapping 单一映射元数据对象。
     */
    public void setMetadata(SingleMappingMetadata singleMapping) {
        metadata.put(singleMapping.getName(), singleMapping);
    }

    // 私有构造函数，用于创建具有指定索引名称的IndexMappingMetadata实例。
    private IndexMappingMetadata(String index) {
        this.index = index;
    }

    // 私有构造函数，用于创建不带索引名称的IndexMappingMetadata实例。
    private IndexMappingMetadata() {
    }

    /**
     * 静态方法，用于获取一个新的IndexMappingMetadata实例。
     *
     * @param index 索引名称。
     * @return 初始化后的IndexMappingMetadata实例。
     */
    public static IndexMappingMetadata getInstance(String index) {
        return new IndexMappingMetadata(index);
    }
}

