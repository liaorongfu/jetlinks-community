package org.jetlinks.community.io.excel.converter;

import lombok.AllArgsConstructor;
import org.hswebframework.reactor.excel.ExcelHeader;
import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.community.utils.ConverterUtils;

import java.util.List;

/**
 * 用于转换数组类型数据的转换器，支持将数组类型数据与Excel单元格内容之间进行相互转换。
 * @since 2.1
 */
@AllArgsConstructor
public class ArrayConverter implements ConverterExcelOption{

    /**
     * 指示是否处理数组类型的数据。
     */
    private boolean array;

    /**
     * 数组元素的类型。
     */
    private Class<?> elementType;

    /**
     * 内部使用的另一个转换器，用于元素类型的转换。
     */
    private ConverterExcelOption converter;


    /**
     * 将数组类型的数据转换为字符串，用于写入Excel。
     * @param val 要转换的数组数据。
     * @param header Excel中的头部信息。
     * @return 转换后的字符串，各个元素通过逗号分隔。
     */
    @Override
    public Object convertForWrite(Object val, ExcelHeader header) {
        // 将数组元素转换为字符串列表，然后将这些字符串用逗号连接起来
        return String.join(",",
            ConverterUtils.convertToList(val, v -> {
                // 如果存在内部转换器，则使用它来转换元素；否则直接将元素转换为字符串
                if (converter == null) {
                    return String.valueOf(v);
                }
                return String.valueOf(converter.convertForWrite(v, header));
            }));
    }

    /**
     * 从Excel单元格内容中读取数据，并转换为数组类型。
     * @param cell Excel单元格中的数据。
     * @param header Excel中的头部信息。
     * @return 转换后的数据，如果{@code array}为true，则返回数组；否则返回元素列表。
     */
    @Override
    public Object convertForRead(Object cell, ExcelHeader header) {

        // 将单元格数据转换为对象列表，每个元素都可能经过内部转换器的转换
        List<Object> list = ConverterUtils
            .convertToList(cell, val -> {
                // 如果存在内部转换器，则使用它来反向转换元素
                if (converter != null) {
                    val = converter.convertForRead(val, header);
                }
                // 如果转换后的值是期望的元素类型，则直接返回；否则尝试进行类型转换
                if (elementType.isInstance(val)) {
                    return val;
                }
                return FastBeanCopier.DEFAULT_CONVERT
                    .convert(val, elementType, FastBeanCopier.EMPTY_CLASS_ARRAY);
            });

        // 根据是否处理数组类型，返回数组或列表
        if (array) {
            return list.toArray();
        }

        return list;
    }
}

