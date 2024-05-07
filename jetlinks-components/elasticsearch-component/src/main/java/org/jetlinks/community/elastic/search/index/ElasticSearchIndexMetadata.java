package org.jetlinks.community.elastic.search.index;

import org.jetlinks.community.elastic.search.utils.ElasticSearchConverter;
import org.jetlinks.core.metadata.PropertyMetadata;

import java.util.List;
import java.util.Map;

/**
 * 定义了ElasticSearch索引元数据的接口。
 * 主要用于管理和转换ElasticSearch索引的元数据。
 */
public interface ElasticSearchIndexMetadata {

    /**
     * 获取索引的名称。
     *
     * @return 索引的名称，类型为String。
     */
    String getIndex();

    /**
     * 获取索引的所有属性元数据列表。
     *
     * @return 索引的属性元数据列表，类型为List<PropertyMetadata>。
     */
    List<PropertyMetadata> getProperties();

    /**
     * 根据属性名称获取对应的属性元数据。
     *
     * @param property 属性名称。
     * @return 对应的属性元数据，类型为PropertyMetadata。
     */
    PropertyMetadata getProperty(String property);

    /**
     * 将普通的Map数据转换为ElasticSearch需要的格式。
     * 使用当前索引元数据中的属性信息进行转换。
     *
     * @param map 普通的Map数据。
     * @return 转换后符合ElasticSearch格式的Map数据。
     */
    default Map<String, Object> convertToElastic(Map<String, Object> map) {
        return ElasticSearchConverter.convertDataToElastic(map, getProperties());
    }

    /**
     * 将ElasticSearch格式的Map数据转换为普通的Map数据。
     * 使用当前索引元数据中的属性信息进行转换。
     *
     * @param map ElasticSearch格式的Map数据。
     * @return 转换后的普通Map数据。
     */
    default Map<String, Object> convertFromElastic(Map<String, Object> map) {
        return ElasticSearchConverter.convertDataFromElastic(map, getProperties());
    }

    /**
     * 创建一个新的ElasticSearch索引元数据实例，仅改变索引名称。
     * 保留当前索引元数据中的属性信息。
     *
     * @param name 新的索引名称。
     * @return 新的ElasticSearch索引元数据实例。
     */
    default ElasticSearchIndexMetadata newIndexName(String name) {
        return new DefaultElasticSearchIndexMetadata(name, getProperties());
    }
}

