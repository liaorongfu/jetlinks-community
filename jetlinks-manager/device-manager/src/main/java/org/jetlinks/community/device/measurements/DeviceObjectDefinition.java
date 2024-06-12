package org.jetlinks.community.device.measurements;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.community.dashboard.ObjectDefinition;

/**
 * 设备对象定义枚举，实现了对象定义接口。
 * 该枚举用于定义设备相关的对象标识和属性。
 */
@Getter
@AllArgsConstructor
public enum DeviceObjectDefinition implements ObjectDefinition {
    /**
     * 设备状态对象定义。
     * 该定义代表了设备的状态属性。
     */
    status("设备状态"),
    /**
     * 设备消息对象定义。
     * 该定义代表了设备的消息属性。
     */
    message("设备消息");

    /**
     * 获取对象的唯一标识。
     * @return 对象的标识字符串，即枚举常量的名称。
     */
    @Override
    public String getId() {
        return name();
    }

    /**
     * 对象的名称。
     * 每个枚举常量都有一个对应的名称属性。
     */
    private String name;
}

