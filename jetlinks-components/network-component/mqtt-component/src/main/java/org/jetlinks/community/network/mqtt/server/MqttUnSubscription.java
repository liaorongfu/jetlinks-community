package org.jetlinks.community.network.mqtt.server;

import io.vertx.mqtt.messages.MqttUnsubscribeMessage;

/**
 * MQTT取消订阅接口。
 * 该接口定义了进行MQTT取消订阅操作的消息模型和相应的确认机制。
 */
public interface MqttUnSubscription {

    /**
     * 获取取消订阅的消息。
     *
     * @return MqttUnsubscribeMessage 返回取消订阅的消息对象，包含了取消订阅的细节。
     */
    MqttUnsubscribeMessage getMessage();

    /**
     * 对取消订阅操作进行确认。
     * 该方法用于在取消订阅操作成功执行后，通知调用者操作已经完成。
     */
    void acknowledge();

}

