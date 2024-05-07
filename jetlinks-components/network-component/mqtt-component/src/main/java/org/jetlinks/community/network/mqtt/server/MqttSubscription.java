package org.jetlinks.community.network.mqtt.server;

import io.vertx.mqtt.messages.MqttSubscribeMessage;

/**
 * MqttSubscription接口定义了MQTT订阅的相关操作。
 * 该接口主要包含两个方法：获取订阅消息和消息确认。
 */
public interface MqttSubscription {

    /**
     * 获取订阅的消息。
     *
     * @return MqttSubscribeMessage 返回订阅到的消息对象。
     */
    MqttSubscribeMessage getMessage();

    /**
     * 对消息进行确认。
     * 该方法用于指示消息已被成功处理。
     */
    void acknowledge();

}

