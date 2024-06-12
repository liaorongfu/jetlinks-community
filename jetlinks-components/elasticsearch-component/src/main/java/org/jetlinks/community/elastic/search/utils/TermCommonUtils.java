package org.jetlinks.community.elastic.search.utils;

import java.util.*;

/**
 * @author bsetfeng
 * @since 1.0
 **/
public class TermCommonUtils {

    /**
     * 将给定的值转换为List<Object>类型。
     * 该方法支持多种类型的输入，并尝试将输入转换为List形式返回。
     * 如果输入为null，返回空列表。
     * 如果输入为String类型，且字符串由逗号分隔，則将每个分隔的部分作为元素转换为List。
     * 如果输入为Object数组，则直接转换为List。
     * 如果输入为Collection类型，则创建一个新的ArrayList，包含输入Collection的所有元素。
     * 对于其他类型的输入，将输入作为一个元素的List返回。
     *
     * @param value 输入值，可以是任何类型。
     * @return 转换后的List<Object>。如果输入为null，则返回空列表。
     */
    public static List<Object> convertToList(Object value) {
        // 如果输入为null，直接返回空列表
        if (value == null) {
            return Collections.emptyList();
        }
        // 如果输入为String类型，尝试按逗号分隔转换为List
        if (value instanceof String) {
            value = ((String) value).split(",");
        }

        // 如果输入为Object数组，转换为List
        if (value instanceof Object[]) {
            value = Arrays.asList(((Object[]) value));
        }

        // 如果输入为Collection类型，创建一个新的ArrayList副本
        if (value instanceof Collection) {
            return new ArrayList<Object>(((Collection) value));
        }

        // 对于其他类型，将输入作为一个元素的List返回
        return Collections.singletonList(value);
    }


    /**
     * 获取标准术语值。
     * 当输入列表的大小为1时，直接返回列表中的唯一元素，否则返回整个列表。
     * 这个方法的设计是为了处理一种特殊情况，即如果有一个单一的值，就直接处理这个值，
     * 如果有多个值，则返回整个列表供进一步处理。
     *
     * @param value 包含标准术语值的列表。这个参数可以包含任意类型的对象。
     * @return 如果列表大小为1，则返回列表中的唯一元素；如果列表大小大于1，则返回整个列表。
     */
    public static Object getStandardsTermValue(List<Object> value) {
        // 检查列表大小是否为1
        if (value.size() == 1) {
            // 如果是，返回列表中的唯一元素
            return value.get(0);
        }
        // 如果列表大小大于1，返回整个列表
        return value;
    }

}
