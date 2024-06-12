package org.jetlinks.community.device.entity.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 设备实例导入导出实体类
 * 用于在Excel导入导出过程中，映射设备实例的相关属性
 * @author Rabbuse
 */
@Data
public class DeviceInstanceImportExportEntity {

    /**
     * 设备ID
     * 在Excel中对应的标题为"设备ID"
     */
    @ExcelProperty("设备ID")
    private String id;

    /**
     * 设备名称
     * 在Excel中对应的标题为"设备名称"
     */
    @ExcelProperty("设备名称")
    private String name;

    /**
     * 产品名称
     * 在Excel中对应的标题为"产品名称"
     */
    @ExcelProperty("产品名称")
    private String productName;

    /**
     * 描述
     * 在Excel中对应的标题为"描述"
     */
    @ExcelProperty("描述")
    private String describe;

    /**
     * 父级设备ID
     * 用于表示设备的层级关系
     * 在Excel中对应的标题为"父级设备ID"
     */
    @ExcelProperty("父级设备ID")
    private String parentId;
}

