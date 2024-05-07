package org.jetlinks.community.gateway.external;

/**
 * 消息接口，定义了消息的基本行为。
 * 提供了获取请求ID、主题、负载、消息内容、消息类型的方法，
 * 以及几个静态工厂方法用于创建不同类型的消息实例。
 */
public interface Message {

    /**
     * 获取请求的ID。
     * @return 请求的唯一标识符。
     */
    String getRequestId();

    /**
     * 获取消息的主题。
     * @return 消息的主题，可能为null。
     */
    String getTopic();

    /**
     * 获取消息的负载。
     * @return 消息的负载数据，可能为null。
     */
    Object getPayload();

    /**
     * 获取消息的内容。
     * @return 消息的内容，可能为null。
     */
    String getMessage();

    /**
     * 获取消息的类型。
     * @return 消息的类型，参见Message.Type。
     */
    Type getType();

    /**
     * 创建一个认证错误消息。
     * @return 返回一个认证失败的简单消息实例。
     */
    static Message authError() {
        // 创建一个认证失败的错误消息
        return new SimpleMessage(null, null, null, Type.authError, "认证失败");
    }

    /**
     * 创建一个错误消息。
     * @param id 消息的请求ID。
     * @param topic 消息的主题。
     * @param message 错误的详细信息。
     * @return 返回一个包含指定ID、主题和错误信息的简单错误消息实例。
     */
    static Message error(String id, String topic, String message) {
        // 创建一个包含错误信息的简单消息
        return new SimpleMessage(id, topic, null, Type.error, message);
    }

    /**
     * 创建一个基于异常的错误消息。
     * @param id 消息的请求ID。
     * @param topic 消息的主题。
     * @param message 异常实例或异常信息。
     * @return 返回一个基于异常信息的简单错误消息实例。
     */
    static Message error(String id, String topic, Throwable message) {
        // 创建一个基于异常的简单错误消息
        return new SimpleMessage(id, topic, null, Type.error, message.getMessage() == null ? message.getClass().getSimpleName() : message.getMessage());
    }

    /**
     * 创建一个成功消息。
     * @param id 消息的请求ID。
     * @param topic 消息的主题。
     * @param payload 消息的负载数据。
     * @return 返回一个表示成功的结果消息实例。
     */
    static Message success(String id, String topic, Object payload) {
        // 创建一个成功结果的消息
        return new SimpleMessage(id, topic, payload, Type.result, null);
    }

    /**
     * 创建一个完成消息。
     * @param id 消息的请求ID。
     * @return 返回一个表示操作完成的简单消息实例。
     */
    static Message complete(String id) {
        // 创建一个操作完成的消息
        return new SimpleMessage(id, null, null, Type.complete, null);
    }

    /**
     * 创建一个Pong响应消息。
     * @param id 消息的请求ID。
     * @return 返回一个Pong类型的简单消息实例。
     */
    static Message pong(String id) {
        // 创建一个Pong响应消息
        return new SimpleMessage(id, null, null, Type.pong, null);
    }

    /**
     * 消息的类型枚举。
     */
    enum Type {
        authError, // 认证错误
        result, // 操作结果
        error, // 错误消息
        complete, // 操作完成
        ping, // Ping请求
        pong // Pong响应
    }
}

