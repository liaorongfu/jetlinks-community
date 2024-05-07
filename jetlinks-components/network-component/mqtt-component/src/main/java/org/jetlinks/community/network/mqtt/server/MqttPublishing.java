package org.jetlinks.community.network.mqtt.server;

import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.core.server.mqtt.MqttPublishingMessage;

/**
 * MqttPublishing接口定义了MQTT消息发布的基本行为，继承自MqttPublishingMessage接口。
 * 它提供了获取消息和消息确认的方法。
 */
public interface MqttPublishing extends MqttPublishingMessage {

    /**
     * 获取待发布的MQTT消息。
     *
     * @return MqttMessage 返回待发布的MQTT消息对象。
     */
    MqttMessage getMessage();

    /**
     * 对消息发布进行确认。
     * 这个方法用于在消息成功发布后，通知调用者或者系统可以进行后续处理或者资源释放。
     */
    void acknowledge();
}

