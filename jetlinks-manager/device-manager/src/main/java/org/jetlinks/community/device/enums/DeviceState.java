package org.jetlinks.community.device.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.Dict;
import org.hswebframework.web.dict.EnumDict;
import org.hswebframework.web.dict.I18nEnumDict;

/**
 * 设备状态枚举类，用于表示设备的三种状态：未激活、离线、在线。
 * 该枚举实现了I18nEnumDict接口，支持国际化。
 * 使用@AllArgsConstructor注解自动为所有构造函数参数生成一个带有参数名的构造函数。
 * 使用@Getter注解为类中的所有字段提供自动化的getter方法。
 * 使用@Dict注解标记该枚举为字典类型，指定其对应的国际化标识为"device-state"。
 * @author Rabbuse
 */
@AllArgsConstructor
@Getter
@Dict("device-state")
public enum DeviceState implements I18nEnumDict<String> {
    notActive("未激活"), // 表示设备未激活的状态
    offline("离线"), // 表示设备离线的状态
    online("在线"); // 表示设备在线的状态

    /**
     * 设备状态的文本描述。
     */
    private final String text;

    /**
     * 获取枚举值的名称。
     *
     * @return 枚举值的名称。
     */
    @Override
    public String getValue() {
        return name();
    }

    /**
     * 根据字节值获取对应的设备状态。
     *
     * @param state 设备状态的字节值。
     * @return 对应的设备状态枚举值。
     */
    public static DeviceState of(byte state) {
        switch (state) {
            case org.jetlinks.core.device.DeviceState.offline:
                return offline;
            case org.jetlinks.core.device.DeviceState.online:
                return online;
            default:
                return notActive;
        }
    }
}

