package org.jetlinks.community.device.enums;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.MessageType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 设备日志类型的枚举，用于表示设备操作的不同类型。
 * 实现了EnumDict接口，以便将枚举值与字符串值映射。
 * @author Rabbuse
 */
@AllArgsConstructor
@Getter
public enum DeviceLogType implements EnumDict<String> {
    event("事件上报"),
    readProperty("读取属性"),
    writeProperty("修改属性"),
    writePropertyReply("修改属性回复"),
    reportProperty("属性上报"),
    readPropertyReply("读取属性回复"),
    child("子设备消息"),
    childReply("子设备消息回复"),
    functionInvoke("调用功能"),
    functionReply("调用功能回复"),
    register("设备注册"),
    unregister("设备注销"),
    log("日志"),
    tag("标签更新"),
    offline("离线"),
    online("上线"),
    other("其它");

    /**
     * 与枚举值对应的文本描述。
     */
    @JSONField(serialize = false)
    private final String text;

    /**
     * 返回枚举值的名称。
     *
     * @return 枚举值的名称。
     */
    @Override
    public String getValue() {
        return name();
    }

    /**
     * 用于将MessageType映射到DeviceLogType的静态映射表。
     * 这允许在设备消息和日志类型之间进行快速转换。
     */
    private final static Map<MessageType, DeviceLogType> typeMapping = new EnumMap<>(MessageType.class);

    static {
        // 初始化类型映射表，将每个MessageType映射到相应的DeviceLogType。
        typeMapping.put(MessageType.EVENT, event);
        typeMapping.put(MessageType.ONLINE, online);
        typeMapping.put(MessageType.OFFLINE, offline);
        typeMapping.put(MessageType.CHILD, child);
        typeMapping.put(MessageType.CHILD_REPLY, childReply);
        typeMapping.put(MessageType.LOG, log);
        typeMapping.put(MessageType.UPDATE_TAG, tag);

        typeMapping.put(MessageType.REPORT_PROPERTY, reportProperty);
        typeMapping.put(MessageType.READ_PROPERTY, readProperty);
        typeMapping.put(MessageType.READ_PROPERTY_REPLY, readPropertyReply);

        typeMapping.put(MessageType.INVOKE_FUNCTION, functionInvoke);
        typeMapping.put(MessageType.INVOKE_FUNCTION_REPLY, functionReply);

        typeMapping.put(MessageType.WRITE_PROPERTY, writeProperty);
        typeMapping.put(MessageType.WRITE_PROPERTY_REPLY, writePropertyReply);

        typeMapping.put(MessageType.REGISTER, register);
        typeMapping.put(MessageType.UN_REGISTER, unregister);
    }

    /**
     * 根据设备消息的类型返回相应的设备日志类型。
     * 如果找不到匹配的日志类型，则返回other。
     *
     * @param message 设备消息对象。
     * @return 对应的设备日志类型。
     */
    public static DeviceLogType of(DeviceMessage message) {
        return Optional.ofNullable(typeMapping.get(message.getMessageType())).orElse(DeviceLogType.other);
    }
}

