package org.jetlinks.community.device.enums;

import lombok.AllArgsConstructor;
import lombok.Generated;
import lombok.Getter;
import org.hswebframework.web.dict.Dict;
import org.hswebframework.web.dict.EnumDict;
import org.jetlinks.core.metadata.Feature;

/**
 * 设备特性枚举，实现了EnumDict和Feature接口。
 * 该枚举用于定义设备可能具有的特定功能或属性。
 * 使用@AllArgsConstructor注解以便通过一个参数来构造枚举值。
 * 使用@Getter注解自动生成getter方法，以便访问枚举常量的属性。
 * 使用@Dict注解为枚举常量提供额外的元数据，例如映射到数据库或字典中的特定键。
 * @author Rabbuse
 */
@Dict("device-feature")
@Getter
@AllArgsConstructor
public enum DeviceFeature implements EnumDict<String>, Feature {
    /**
     * 子设备自己管理状态的特性。
     * 这意味着子设备不需要依赖于父设备来管理其状态。
     */
    selfManageState("子设备自己管理状态");


    private final String text;

    /**
     * 获取枚举常量的名称作为其值。
     * 该方法重写了Enum类中的name()方法，并应用了@Generated注解，表示该方法是由编译器生成的。
     *
     * @return 枚举常量的名称。
     */
    @Override
    @Generated
    public String getValue() {
        return name();
    }

    /**
     * 获取枚举常量的名称作为其ID。
     * 该方法实现了EnumDict接口中的getId方法，用于获取枚举常量的唯一标识。
     *
     * @return 枚举常量的名称作为ID。
     */
    @Override
    public String getId() {
        return getValue();
    }

    /**
     * 获取枚举常量的文本描述。
     * 该方法实现了Feature接口中的getName方法，用于获取枚举常量的文本描述。
     *
     * @return 枚举常量的文本描述。
     */
    @Override
    public String getName() {
        return text;
    }
}

