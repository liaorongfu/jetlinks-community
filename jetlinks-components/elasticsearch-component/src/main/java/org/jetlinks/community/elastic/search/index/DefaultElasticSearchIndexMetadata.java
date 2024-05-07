package org.jetlinks.community.elastic.search.index;

import org.jetlinks.core.metadata.DataType;
import org.jetlinks.core.metadata.PropertyMetadata;
import org.jetlinks.core.metadata.SimplePropertyMetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认的ElasticSearch索引元数据实现类。
 * 用于存储和管理ElasticSearch索引的元数据，包括索引名称和属性元数据。
 */
public class DefaultElasticSearchIndexMetadata implements ElasticSearchIndexMetadata {
    private String index; // 索引名称

    // 属性映射，键为属性名，值为属性元数据
    private Map<String, PropertyMetadata> properties = new HashMap<>();

    /**
     * 单参数构造函数，初始化索引名称。
     * @param index 索引名称，将被转换为小写并去除首尾空格。
     */
    public DefaultElasticSearchIndexMetadata(String index) {
        this.index = index.toLowerCase().trim();
    }

    /**
     * 双参数构造函数，初始化索引名称和属性列表。
     * @param index 索引名称，将被转换为小写并去除首尾空格。
     * @param properties 属性列表，每个属性将被添加到索引的属性映射中。
     */
    public DefaultElasticSearchIndexMetadata(String index, List<PropertyMetadata> properties) {
        this(index);
        properties.forEach(this::addProperty); // 遍历并添加所有属性
    }

    /**
     * 获取指定属性的元数据。
     * @param property 属性名称。
     * @return 返回属性的元数据，如果不存在，则返回null。
     */
    @Override
    public PropertyMetadata getProperty(String property) {
        return properties.get(property);
    }

    /**
     * 获取索引名称。
     * @return 返回处理后的索引名称。
     */
    @Override
    public String getIndex() {
        return index;
    }

    /**
     * 获取所有属性的列表。
     * @return 返回属性元数据的列表，新列表实例。
     */
    @Override
    public List<PropertyMetadata> getProperties() {
        return new ArrayList<>(properties.values());
    }

    /**
     * 添加一个属性到索引的属性映射中。
     * @param property 属性元数据。
     * @return 返回当前实例，支持链式调用。
     */
    public DefaultElasticSearchIndexMetadata addProperty(PropertyMetadata property) {
        properties.put(property.getId(), property);
        return this;
    }

    /**
     * 添加一个属性及其数据类型到索引的属性映射中。
     * @param property 属性名称。
     * @param type 属性的数据类型。
     * @return 返回当前实例，支持链式调用。
     */
    public DefaultElasticSearchIndexMetadata addProperty(String property, DataType type) {
        SimplePropertyMetadata metadata=new SimplePropertyMetadata();
        metadata.setValueType(type); // 设置属性的数据类型
        metadata.setId(property); // 设置属性名称
        properties.put(property, metadata); // 添加到属性映射
        return this;
    }
}

