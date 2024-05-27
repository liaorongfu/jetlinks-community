package org.jetlinks.community.io.excel.converter;

import lombok.AllArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.hswebframework.reactor.excel.ExcelHeader;
import org.hswebframework.reactor.excel.WritableCell;
import org.hswebframework.reactor.excel.poi.options.CellOption;
import org.jetlinks.reactor.ql.utils.CastUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * 日期转换器类，用于在Java对象与Excel单元格之间进行日期类型的转换。
 */
@AllArgsConstructor
public class DateConverter implements ConverterExcelOption, CellOption {

    private final String format; // 日期格式字符串
    private final Class<?> javaType; // 目标Java类型

    /**
     * 将对象中的日期值转换为指定格式的字符串。
     *
     * @param val 需要转换的日期对象。
     * @param header Excel头信息，当前方法不使用该参数，可忽略。
     * @return 转换后的日期字符串。
     */
    @Override
    public Object convertForWrite(Object val, ExcelHeader header) {
        return new DateTime(CastUtils.castDate(val)).toString(format);
    }

    /**
     * 将Excel单元格中的日期值转换为指定的Java类型。
     *
     * @param val Excel单元格中的值。
     * @param header Excel头信息，当前方法不使用该参数，可忽略。
     * @return 转换后的Java对象，其类型为javaType参数指定的类型。
     */
    @Override
    public Object convertForRead(Object val, ExcelHeader header) {

        if (null == val) {
            return null;
        }

        if (javaType.isInstance(val)) {
            return val; // 如果值已经是目标类型，则直接返回
        }
        Date date = CastUtils.castDate(val);
        if (javaType == Long.class || javaType == long.class) {
            return date.getTime(); // 如果目标类型为Long或long，返回时间戳
        }
        if (javaType == LocalDateTime.class) {
            return java.time.LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()); // 如果目标类型为LocalDateTime，进行转换
        }
        if (javaType == LocalDate.class) {
            return java.time.LocalDateTime
                .ofInstant(date.toInstant(), ZoneId.systemDefault())
                .toLocalDate(); // 如果目标类型为LocalDate，进一步转换为LocalDate
        }

        return date; // 如果目标类型为Date或其他不支持的类型，直接返回原始Date对象
    }

    /**
     * 设置Excel单元格的样式，以匹配日期的显示格式。
     *
     * @param poiCell Apache POI表示的单元格对象。
     * @param cell JExcelApi表示的单元格对象，当前方法不使用该参数，可忽略。
     */
    @Override
    public void cell(org.apache.poi.ss.usermodel.Cell poiCell, WritableCell cell) {
        CellStyle style = poiCell.getCellStyle();
        if (style == null) {
            style = poiCell.getRow()
                           .getSheet()
                           .getWorkbook()
                           .createCellStyle();
            poiCell.setCellStyle(style);
        }
        DataFormat dataFormat = poiCell
            .getRow()
            .getSheet()
            .getWorkbook()
            .createDataFormat();

        style.setDataFormat(dataFormat.getFormat(format)); // 设置单元格样式，使其符合指定的日期格式
    }
}

