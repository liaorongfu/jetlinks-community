package org.jetlinks.community.gateway;

import org.jetlinks.core.message.codec.EncodedMessage;

/**
 * EncodableMessage接口定义了可编码消息的契约，扩展自EncodedMessage接口。
 * 它提供了一个方法来获取消息的原始载体，并提供了一个静态工厂方法来创建EncodableMessage实例。
 */
public interface EncodableMessage extends EncodedMessage {

    /**
     * 获取消息的原始载体对象。这个方法允许访问消息的底层数据表示，
     * 该表示可以是任意类型，具体取决于消息的实现方式。
     *
     * @return 消息的原始载体对象，其类型可以是任意对象。
     */
    Object getNativePayload();

    /**
     * 一个静态工厂方法，用于创建一个新的EncodableMessage实例。
     * 这个方法接受一个任意类型的对象作为参数，并将其封装为一个EncodableMessage实例。
     * 具体实现中，该方法返回一个JsonEncodedMessage实例，表明默认的编码方式是JSON。
     *
     * @param object 要封装为EncodableMessage的消息载体对象。
     * @return 一个新的EncodableMessage实例，其底层载体为传入的对象。
     */
    static EncodableMessage of(Object object) {
        return new JsonEncodedMessage(object);
    }
}

