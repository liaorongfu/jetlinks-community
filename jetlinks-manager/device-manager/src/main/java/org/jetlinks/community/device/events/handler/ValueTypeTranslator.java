package org.jetlinks.community.device.events.handler;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.metadata.DataType;
import org.jetlinks.core.metadata.PropertyMetadata;
import org.jetlinks.core.metadata.types.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author bsetfeng
 * @since 1.0
 **/
@Slf4j
public class ValueTypeTranslator {

    /**
     * 根据属性元数据转换值。
     *
     * 此方法用于处理给定值的转换，根据值的类型决定如何处理。如果值是Map类型，则使用特定的逻辑进行转换，
     * 否则将值转换为JSON格式。
     *
     * @param value 需要转换的值，可以是任何类型。
     * @param metadataList 属性元数据列表，用于在转换Map类型值时提供额外的处理信息。
     * @return 转换后的值，可能是JSON字符串或经过特定处理的Map对象。
     */
    private static Object propertyMetadataTranslator(Object value, List<PropertyMetadata> metadataList) {
        // 判断输入值是否为Map类型
        if (value instanceof Map) {
            // 对Map类型的值进行特定的转换处理
            return propertyMetadataToMap((Map<String, Object>) value, metadataList);
        } else {
            // 对非Map类型的值，直接转换为JSON格式
            return JSON.toJSON(value);
        }
    }


    /**
     * 将属性元数据列表转换为Map格式，以便于后续处理。
     * 该方法主要功能是根据提供的属性元数据列表，对给定的属性映射进行值类型的转换。
     * 它遍历输入的属性映射，如果某个属性在元数据列表中存在对应的元数据，
     * 则使用翻译函数将该属性的值转换为元数据中指定的类型。
     *
     * @param map 原始的属性映射，其键值对需要根据元数据进行可能的类型转换。
     * @param metadataList 属性元数据的列表，每个元素包含了属性名、属性类型等信息。
     * @return 返回转换后的属性映射，其中的值可能已经被转换为对应的类型。
     */
    private static Map<String, Object> propertyMetadataToMap(Map<String, Object> map, List<PropertyMetadata> metadataList) {
        // 将属性元数据列表转换为映射，以便快速查找特定属性的元数据
        Map<String, PropertyMetadata> metadataMap = toMap(metadataList);

        // 遍历原始属性映射中的每个条目
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            // 根据当前条目的键查找对应的属性元数据
            PropertyMetadata property = metadataMap.get(entry.getKey());
            // 如果找到了对应的属性元数据
            if (null != property) {
                // 使用翻译函数将当前条目的值转换为元数据指定的类型，并更新到映射中
                entry.setValue(translator(entry.getValue(), property.getValueType()));
            }
        }
        // 返回经过类型转换的属性映射
        return map;
    }


    /**
     * 将属性元数据列表转换为映射表，映射表的键为属性元数据的ID。
     * 这样做的目的是为了通过属性的ID快速查询属性元数据。
     *
     * @param metadata 属性元数据列表，每个元素包含一个属性的详细信息。
     * @return 返回一个映射表，其中键是属性元数据的ID，值是相应的属性元数据对象。
     */
    private static Map<String, PropertyMetadata> toMap(List<PropertyMetadata> metadata) {
        // 使用流式编程将属性元数据列表转换为映射表
        // 映射表的键是每个属性元数据的ID，值是属性元数据本身
        return metadata.stream()
                .collect(Collectors.toMap(PropertyMetadata::getId, Function.identity()));
    }


    /**
     * 根据数据类型转换器对象。
     * 该方法旨在根据给定的数据类型将输入值转换为相应的类型。支持的类型包括日期时间、双精度浮点数、单精度浮点数、长整型、布尔型、整型以及对象类型。
     * 对于不支持的类型，直接返回原始输入值。
     * 在转换过程中出现异常时，会捕获异常并记录错误信息，然后返回原始输入值。
     *
     * @param value 待转换的原始值。
     * @param dataType 原始值对应的数据类型。
     * @return 转换后的值，如果无法转换则返回原始值。
     */
    public static Object translator(Object value, DataType dataType) {
        try {
            // 根据数据类型实例进行类型转换
            if (dataType instanceof DateTimeType) {
                return ((DateTimeType) dataType).convert(value);
            } else if (dataType instanceof DoubleType) {
                return ((DoubleType) dataType).convert(value);
            } else if (dataType instanceof FloatType) {
                return ((FloatType) dataType).convert(value);
            } else if (dataType instanceof LongType) {
                return ((LongType) dataType).convert(value);
            } else if (dataType instanceof BooleanType) {
                return ((BooleanType) dataType).convert(value);
            } else if (dataType instanceof IntType) {
                return ((IntType) dataType).convert(value);
            } else if (dataType instanceof ObjectType) {
                // 对象类型的转换通过调用另一个方法完成
                return propertyMetadataTranslator(value, ((ObjectType) dataType).getProperties());
            } else {
                // 对于不支持的类型，直接返回原始值
                return value;
            }
        } catch (Exception e) {
            // 捕获并记录转换过程中可能出现的异常
            log.error("设备上报值与元数据值不匹配.value:{},DataTypeClass:{}", value, dataType.getClass(), e);
            return value;
        }
    }

//    public static String dateFormatTranslator(Date date) {
//        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//        Instant instant = date.toInstant();
//        ZoneId zone = ZoneId.systemDefault();
//        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, zone);
//        return localDateTime.format(dtf);
//    }
}
