package org.jetlinks.community.device.enums;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.Dict;
import org.hswebframework.web.dict.EnumDict;

/**
 * 设备类型的枚举定义。
 * 该枚举用于区分不同类型的设备，包括直连设备、网关子设备和网关设备。
 * <p>
 * 使用@AllArgsConstructor注解以便能够通过构造函数传递文本描述。
 * 使用@Getter注解自动生成getter方法，方便获取枚举值的文本描述。
 * 使用@Dict注解将该枚举注册为字典类型，便于在系统中作为字典使用。
 * 使用@JsonDeserialize注解指定枚举的反序列化方式，以便从JSON中正确解析枚举值。
 * @author Rabbuse
 */
@AllArgsConstructor
@Getter
@Dict("device-type")
@JsonDeserialize(contentUsing = EnumDict.EnumDictJSONDeserializer.class)
public enum DeviceType implements EnumDict<String> {
    /**
     * 直连设备类型。
     * 这种类型的设备直接与系统通信，不通过网关。
     */
    device("直连设备"),
    /**
     * 网关子设备类型。
     * 这种类型的设备通过网关与系统通信，自身不直接与系统交互。
     */
    childrenDevice("网关子设备"),
    /**
     * 网关设备类型。
     * 这种类型的设备作为其他设备的网关，负责转发通信。
     */
    gateway("网关设备")
    ;

    /**
     * 设备类型的文本描述。
     * 这个字段用于存储设备类型的友好名称，便于展示给用户。
     */
    private final String text;

    /**
     * 获取枚举值的名称。
     * 该方法重写了Enum的getValue方法，返回枚举值的名称作为字典值。
     *
     * @return 枚举值的名称。
     */
    @Override
    public String getValue() {
        return name();
    }

}

