package org.jetlinks.community.io.excel.converter;

import org.hswebframework.reactor.excel.ExcelHeader;
import org.hswebframework.reactor.excel.ExcelOption;

/**
 * ConverterExcelOption 接口扩展自 ExcelOption 接口，提供了针对 Excel 数据读写转换的自定义选项。
 * 该接口定义了两个方法，用于在写入和读取 Excel 数据时对值进行转换。
 * @since 2.1
 */
public interface ConverterExcelOption extends ExcelOption {

    /**
     * 用于在写入 Excel 之前对数据进行转换。
     *
     * @param val 需要写入 Excel 的原始值。
     * @param header 对应单元格的 ExcelHeader 对象，包含了头部信息。
     * @return 转换后的值，将被写入到 Excel 中。
     */
    Object convertForWrite(Object val, ExcelHeader header);

    /**
     * 用于在从 Excel 读取数据时对值进行转换。
     *
     * @param cell 从 Excel 中读取到的单元格对象。
     * @param header 对应单元格的 ExcelHeader 对象，包含了头部信息。
     * @return 转换后的值，将被作为业务逻辑处理的数据。
     */
    Object convertForRead(Object cell, ExcelHeader header);

}

