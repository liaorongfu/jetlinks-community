package org.jetlinks.community.network.mqtt.server;

import org.jetlinks.community.network.ServerNetwork;
import reactor.core.publisher.Flux;

/**
 * MQTT服务端接口，定义了MQTT服务端的基本行为，继承自ServerNetwork接口。
 *
 * @author zhouhao
 * @version 1.0
 * @since 1.0
 */
public interface MqttServer extends ServerNetwork {

    /**
     * 处理客户端连接的方法。该方法用于订阅客户端的连接事件，当有客户端连接时，会返回相应的MqttConnection对象的Flux流。
     *
     * @return 返回一个Flux<MqttConnection>，表示客户端连接的流，该流可以用于处理所有的客户端连接事件。
     */
    Flux<MqttConnection> handleConnection();

    /**
     * 处理指定holder的客户端连接的方法。该方法用于订阅特定holder的客户端连接事件，当有客户端连接到指定holder时，会返回相应的MqttConnection对象的Flux流。
     *
     * @param holder 指定的holder名称，用于区分不同的客户端连接。
     * @return 返回一个Flux<MqttConnection>，表示特定holder的客户端连接的流，该流可以用于处理特定holder的所有客户端连接事件。
     */
    Flux<MqttConnection> handleConnection(String holder);

}

