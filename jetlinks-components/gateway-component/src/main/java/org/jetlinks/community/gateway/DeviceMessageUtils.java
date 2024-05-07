package org.jetlinks.community.gateway;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.jetlinks.core.event.TopicPayload;
import org.jetlinks.core.message.CommonDeviceMessage;
import org.jetlinks.core.message.CommonDeviceMessageReply;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.MessageType;
import org.jetlinks.core.message.property.*;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 设备消息工具类，提供一系列静态方法用于消息的转换、属性设置和获取等操作。
 */
public class DeviceMessageUtils {

    /**
     * 将TopicPayload对象转换为DeviceMessage对象。
     *
     * @param message TopicPayload消息对象。
     * @return 转换后的DeviceMessage对象的Optional实例。
     */
    @SuppressWarnings("all")
    public static Optional<DeviceMessage> convert(TopicPayload message) {
        return Optional.of(message.decode(DeviceMessage.class));
    }

    /**
     * 将ByteBuf对象转换为DeviceMessage对象。
     *
     * @param payload 包含消息数据的ByteBuf对象。
     * @return 转换后的DeviceMessage对象的Optional实例。
     * @throws RuntimeException 当消息转换失败时抛出。
     */
    public static Optional<DeviceMessage> convert(ByteBuf payload) {
        try {
            return MessageType.convertMessage(JSON.parseObject(payload.toString(StandardCharsets.UTF_8)));
        } finally {
            ReferenceCountUtil.safeRelease(payload); // 释放ByteBuf资源，防止内存泄漏。
        }
    }

    /**
     * 尝试为设备消息设置属性。
     *
     * @param message 待设置属性的设备消息对象。
     * @param properties 要设置的属性键值对。
     */
    public static void trySetProperties(DeviceMessage message, Map<String, Object> properties) {
        // 根据消息类型，将属性设置到相应的消息对象中。
        if (message instanceof ReportPropertyMessage) {
            ((ReportPropertyMessage) message).setProperties(properties);
        } else if (message instanceof ReadPropertyMessageReply) {
            ((ReadPropertyMessageReply) message).setProperties(properties);
        } else if (message instanceof WritePropertyMessageReply) {
            ((WritePropertyMessageReply) message).setProperties(properties);
        }
    }

    /**
     * 尝试从设备消息中获取属性。
     *
     * @param message 待获取属性的设备消息对象。
     * @return 消息中包含的属性键值对的Optional实例。
     */
    public static Optional<Map<String, Object>> tryGetProperties(DeviceMessage message) {
        // 如果消息是PropertyMessage类型，则尝试获取其属性。
        if (message instanceof PropertyMessage) {
            return Optional.ofNullable(((PropertyMessage) message).getProperties());
        }

        return Optional.empty();
    }

    /**
     * 尝试从设备消息中获取属性来源时间。
     *
     * @param message 待获取属性来源时间的设备消息对象。
     * @return 消息中包含的属性来源时间的Optional实例。
     */
    public static Optional<Map<String, Long>> tryGetPropertySourceTimes(DeviceMessage message) {
        // 如果消息是PropertyMessage类型，则尝试获取其属性来源时间。
        if (message instanceof PropertyMessage) {
            return Optional.ofNullable(((PropertyMessage) message).getPropertySourceTimes());
        }
        return Optional.empty();
    }

    /**
     * 尝试从设备消息中获取属性状态。
     *
     * @param message 待获取属性状态的设备消息对象。
     * @return 消息中包含的属性状态的Optional实例。
     */
    public static Optional<Map<String, String>> tryGetPropertyStates(DeviceMessage message) {
        // 如果消息是PropertyMessage类型，则尝试获取其属性状态。
        if (message instanceof PropertyMessage) {
            return Optional.ofNullable(((PropertyMessage) message).getPropertyStates());
        }
        return Optional.empty();
    }

    /**
     * 尝试获取设备消息中的完整属性列表。
     *
     * @param message 待获取完整属性列表的设备消息对象。
     * @return 消息中包含的完整属性列表，如果不存在则返回空列表。
     */
    public static List<Property> tryGetCompleteProperties(DeviceMessage message) {
        // 如果消息是PropertyMessage类型，则尝试获取其完整属性列表。
        if (message instanceof PropertyMessage) {
            return ((PropertyMessage) message).getCompleteProperties();
        }

        return Collections.emptyList();
    }

    /**
     * 尝试为设备消息设置设备ID。
     *
     * @param message 待设置设备ID的设备消息对象。
     * @param deviceId 要设置的设备ID。
     */
    public static void trySetDeviceId(DeviceMessage message, String deviceId) {
        // 根据消息类型，将设备ID设置到相应的消息对象中。
        if (message instanceof CommonDeviceMessage) {
            ((CommonDeviceMessage) message).setDeviceId(deviceId);
        } else if (message instanceof CommonDeviceMessageReply) {
            ((CommonDeviceMessageReply<?>) message).setDeviceId(deviceId);
        }
    }

    /**
     * 尝试为设备消息设置消息ID。
     *
     * @param message 待设置消息ID的设备消息对象。
     * @param messageId 消息ID的生成器，提供唯一的消息ID。
     */
    public static void trySetMessageId(DeviceMessage message, Supplier<String> messageId) {
        // 如果消息ID已经存在，则不设置；否则根据消息类型设置消息ID。
        if (StringUtils.hasText(message.getMessageId())) {
            return;
        }

        if (message instanceof CommonDeviceMessage) {
            ((CommonDeviceMessage) message).setMessageId(messageId.get());
        } else if (message instanceof CommonDeviceMessageReply) {
            ((CommonDeviceMessageReply<?>) message).setMessageId(messageId.get());
        }
    }

}

