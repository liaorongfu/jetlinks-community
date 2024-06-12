package org.jetlinks.community.device.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.Dict;
import org.hswebframework.web.dict.EnumDict;

/**
 * 设备产品状态枚举，用于表示设备产品的注册状态。
 * 使用@AllArgsConstructor注解以便于通过名称和值初始化枚举常量。
 * 使用@Getter注解为枚举提供自动生成的getter方法。
 * 使用@Dict注解将枚举与字典系统关联，指定枚举的字典类型为"device-product-state"。
 * @author Rabbuse
 */
@AllArgsConstructor
@Getter
@Dict("device-product-state")
public enum DeviceProductState implements EnumDict<Byte> {
    /**
     * 未发布状态，表示设备产品尚未发布。
     * 值为0。
     */
    unregistered("未发布", (byte) 0),
    /**
     * 已发布状态，表示设备产品已经发布。
     * 值为1。
     */
    registered("已发布", (byte) 1),
    /**
     * 其他状态，用于表示不归属于已定义状态的其他情况。
     * 值为-100。
     */
    other("其它", (byte) -100),
    /**
     * 禁用状态，表示设备产品被禁用。
     * 值为-1。
     */
    forbidden("禁用", (byte) -1);

    /**
     * 枚举状态的文本描述。
     */
    private String text;
    /**
     * 枚举状态对应的字节值。
     */
    private Byte value;

    /**
     * 获取枚举常量的名称。
     *
     * @return 枚举常量的名称。
     */
    public String getName() {
        return name();
    }

    /**
     * 根据字节值查找对应的设备产品状态。
     * 如果找不到匹配的状态，则返回other状态。
     *
     * @param state 字节值，表示设备产品状态。
     * @return 对应的设备产品状态枚举常量。
     */
    public static DeviceProductState of(byte state) {
        return EnumDict.findByValue(DeviceProductState.class, state)
                .orElse(other);
    }
}

