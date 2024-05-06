package org.jetlinks.community.topic;

import lombok.Generated;
import org.jetlinks.core.utils.StringBuilderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 提供与主题相关的常量定义和事件生成方法。
 */
public interface Topics {

    // 设备注册事件的主题模板
    String allDeviceRegisterEvent = "/_sys/registry-device/*/register";
    // 设备注销事件的主题模板
    String allDeviceUnRegisterEvent = "/_sys/registry-device/*/unregister";
    // 设备元数据变更事件的主题模板
    String allDeviceMetadataChangedEvent = "/_sys/registry-device/*/metadata";

    /**
     * 生成设备注册事件的主题。
     *
     * @param deviceId 设备ID
     * @return 设备注册事件的主题字符串
     */
    @Generated
    static String deviceRegisterEvent(String deviceId) {
        return registryDeviceEvent(deviceId, "register");
    }

    /**
     * 生成设备注销事件的主题。
     *
     * @param deviceId 设备ID
     * @return 设备注销事件的主题字符串
     */
    @Generated
    static String deviceUnRegisterEvent(String deviceId) {
        return registryDeviceEvent(deviceId, "unregister");
    }

    /**
     * 生成设备元数据变更事件的主题。
     *
     * @param deviceId 设备ID
     * @return 设备元数据变更事件的主题字符串
     */
    @Generated
    static String deviceMetadataChangedEvent(String deviceId) {
        return registryDeviceEvent(deviceId, "metadata");
    }

    // 产品注册事件的主题模板
    String allProductRegisterEvent = "/_sys/registry-product/*/register";
    // 产品注销事件的主题模板
    String allProductUnRegisterEvent = "/_sys/registry-product/*/unregister";
    // 产品元数据变更事件的主题模板
    String allProductMetadataChangedEvent = "/_sys/registry-product/*/metadata";

    /**
     * 生成产品注册事件的主题。
     *
     * @param deviceId 产品ID
     * @return 产品注册事件的主题字符串
     */
    @Generated
    static String productRegisterEvent(String deviceId) {
        return registryProductEvent(deviceId, "register");
    }

    /**
     * 生成产品注销事件的主题。
     *
     * @param deviceId 产品ID
     * @return 产品注销事件的主题字符串
     */
    @Generated
    static String productUnRegisterEvent(String deviceId) {
        return registryProductEvent(deviceId, "unregister");
    }

    /**
     * 生成产品元数据变更事件的主题。
     *
     * @param deviceId 产品ID
     * @return 产品元数据变更事件的主题字符串
     */
    @Generated
    static String productMetadataChangedEvent(String deviceId) {
        return registryProductEvent(deviceId, "metadata");
    }

    /**
     * 根据设备ID和事件类型生成设备事件的主题。
     *
     * @param deviceId  设备ID
     * @param eventType 事件类型
     * @return 设备事件的主题字符串
     */
    static String registryDeviceEvent(String deviceId, String eventType) {
        return "/_sys/registry-device/" + deviceId + "/" + eventType;
    }

    /**
     * 根据产品ID和事件类型生成产品事件的主题。
     *
     * @param deviceId  产品ID
     * @param eventType 事件类型
     * @return 产品事件的主题字符串
     */
    static String registryProductEvent(String deviceId, String eventType) {
        return "/_sys/registry-product/" + deviceId + "/" + eventType;
    }

    /**
     * 生成告警事件的主题。
     *
     * @param targetType 目标类型（如设备、产品等）
     * @param targetId   目标ID
     * @param alarmId    告警ID
     * @return 告警事件的主题字符串
     */
    static String alarm(String targetType, String targetId, String alarmId) {
        // 生成告警记录的主题
        return String.join("", "/alarm/", targetType, "/", targetId, "/", alarmId, "/record");
    }

    /**
     * 提供与用户认证相关的主题定义和方法。
     */
    interface Authentications {

        // 用户认证信息变更事件的主题模板
        String allUserAuthenticationChanged = "/_sys/user-dimension-changed/*";

        /**
         * 生成指定用户认证信息变更事件的主题。
         *
         * @param userId 用户ID
         * @return 用户认证信息变更事件的主题字符串
         */
        static String userAuthenticationChanged(String userId) {
            return "/_sys/user-dimension-changed/" + userId;
        }

    }
}
