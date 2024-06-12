package org.jetlinks.community.elastic.search.utils;

import com.google.common.collect.Collections2;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.hswebframework.ezorm.core.param.QueryParam;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexMetadata;
import org.jetlinks.core.metadata.Converter;
import org.jetlinks.core.metadata.DataType;
import org.jetlinks.core.metadata.PropertyMetadata;
import org.jetlinks.core.metadata.types.*;

import java.util.*;

/**
 * @author Rabbuse
 */
public class ElasticSearchConverter {


    /**
     * 将查询参数转换为Elasticsearch的SearchSourceBuilder对象。
     * 此方法作为转换过程的入口点，它封装了QueryParam到SearchSourceBuilder的转换逻辑。
     *
     * @param queryParam 查询参数对象，包含了需要进行搜索的条件和配置。
     * @param metadata Elasticsearch索引的元数据，用于辅助构建查询条件。
     * @return 返回一个SearchSourceBuilder对象，该对象配置了相应的查询条件和设置。
     */
    public static SearchSourceBuilder convertSearchSourceBuilder(QueryParam queryParam, ElasticSearchIndexMetadata metadata) {
        return QueryParamTranslator.convertSearchSourceBuilder(queryParam, metadata);
    }

    /**
     * 将数据转换为适合Elasticsearch存储的格式。
     * 此方法主要用于处理特定的数据类型，如地理位置、日期时间等，将它们转换为Elasticsearch可识别的格式。
     *
     * @param data 原始数据，以键值对形式表示。
     * @param properties 属性元数据列表，包含每个属性的ID、类型等信息。
     * @return 转换后的数据，以键值对形式表示，格式适合Elasticsearch。
     */
    public static Map<String, Object> convertDataToElastic(Map<String, Object> data,
                                                           List<PropertyMetadata> properties) {
        // 创建一个新的HashMap来存储转换后的数据，以避免修改原始数据。
        Map<String, Object> newValue = new HashMap<>(data);
        // 遍历属性列表，对每个属性进行类型转换。
        for (PropertyMetadata property : properties) {
            // 获取属性的类型。
            DataType type = property.getValueType();
            // 获取属性的值。
            Object val = data.get(property.getId());
            // 如果属性值为空，则跳过当前属性。
            if (val == null) {
                continue;
            }
            // 处理地理位置类型。
            //处理地理位置类型
            if (type instanceof GeoType) {
                GeoPoint point = ((GeoType) type).convert(val);

                Map<String, Object> geoData = new HashMap<>();
                // 将地理位置的纬度和经度分别存储到geoData中。
                geoData.put("lat", point.getLat());
                geoData.put("lon", point.getLon());

                // 将转换后的地理位置数据存储到newValue中。
                newValue.put(property.getId(), geoData);
            } else if (type instanceof GeoShapeType) {
                GeoShape shape = ((GeoShapeType) type).convert(val);
                // 如果转换后的形状为null，则抛出异常。
                if (shape == null) {
                    throw new UnsupportedOperationException("不支持的GeoShape格式:" + val);
                }
                Map<String, Object> geoData = new HashMap<>();
                // 将形状的类型和坐标存储到geoData中。
                geoData.put("type", shape.getType().name());
                geoData.put("coordinates", shape.getCoordinates());
                // 将转换后的几何形状数据存储到newValue中。
                newValue.put(property.getId(), geoData);
            } else if (type instanceof DateTimeType) {
                Date date = ((DateTimeType) type).convert(val);
                // 将日期时间转换为毫秒数存储到newValue中。
                newValue.put(property.getId(), date.getTime());
            } else if (type instanceof Converter) {
                // 使用Converter接口进行类型转换，并将转换后的值存储到newValue中。
                newValue.put(property.getId(), ((Converter<?>) type).convert(val));
            }
        }
        // 返回转换后的数据。
        return newValue;
    }


    /**
     * 将Elasticsearch的数据转换为更合适的形式。
     * 此方法主要用于处理特定的数据类型，如地理位置、日期时间、对象等，将它们转换为更易处理的格式。
     *
     * @param data 来自Elasticsearch的原始数据映射。
     * @param properties 数据属性的元数据列表，包含每个属性的类型信息。
     * @return 转换后的数据映射，其中特定类型的值已被转换为更合适的格式。
     */
    public static Map<String, Object> convertDataFromElastic(Map<String, Object> data,
                                                             List<PropertyMetadata> properties) {
        // 创建一个新的映射，以避免修改原始数据
        Map<String, Object> newData = new HashMap<>(data);
        for (PropertyMetadata property : properties) {
            DataType type = property.getValueType();
            Object val = newData.get(property.getId());
            // 如果值为空，则跳过当前属性
            if (val == null) {
                continue;
            }
            // 处理地理位置类型
            //处理地理位置类型
            if (type instanceof GeoType) {
                newData.put(property.getId(), ((GeoType) type).convertToMap(val));
            } else if (type instanceof GeoShapeType) {
                // 处理地理形状类型
                newData.put(property.getId(), GeoShape.of(val).toMap());
            } else if (type instanceof DateTimeType) {
                // 处理日期时间类型
                Date date = ((DateTimeType) type).convert(val);
                newData.put(property.getId(), date);
            } else if (type instanceof ObjectType) {
                // 处理对象类型
                if (val instanceof Collection) {
                    // 如果值是集合，则对集合中的每个元素进行转换
                    val = Collections2.transform(((Collection<?>) val), ((ObjectType) type)::convert);
                    newData.put(property.getId(), val);
                } else {
                    newData.put(property.getId(), ((ObjectType) type).convert(val));
                }
            } else if (type instanceof Converter) {
                // 处理自定义转换器类型
                newData.put(property.getId(), ((Converter<?>) type).convert(val));
            }
        }
        return newData;
    }

}