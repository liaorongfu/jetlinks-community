package org.jetlinks.community.device.message.transparent;

import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DirectDeviceMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 透明消息编码解码接口。
 * 该接口定义了对设备消息进行编码和解码的方法，旨在提供一种透明的、与消息内容格式无关的消息处理机制。
 * <p>
 * 解码方法接收一种特定格式的设备消息（DirectDeviceMessage），并将其转换为更通用的设备消息格式（DeviceMessage）。
 * 编码方法则将通用设备消息格式转换为特定的直接设备消息格式，以便于直接发送给设备。
 */
public interface TransparentMessageCodec {

    /**
     * 解码设备消息。
     * <p>
     * 该方法接收一个DirectDeviceMessage对象，它包含了从设备接收的原始消息。
     * 方法的目的是将这种特定格式的消息转换为一个Flux流，其中每个元素都是一个DeviceMessage对象。
     * 这样做的目的是为了提供一个可扩展的、异步的处理机制，允许对设备消息进行过滤、转换等操作。
     *
     * @param message 待解码的直接设备消息。
     * @return 返回一个Flux流，其中包含解码后的设备消息。
     */
    Flux<DeviceMessage> decode(DirectDeviceMessage message);

    /**
     * 编码设备消息。
     * <p>
     * 该方法接收一个DeviceMessage对象，它代表了需要发送到设备的通用消息。
     * 方法的目的是将这个通用消息格式转换为一个DirectDeviceMessage对象，以便可以直接发送到设备。
     * 这个过程可能涉及到消息内容的格式转换、添加额外的头部信息等操作。
     *
     * @param message 待编码的设备消息。
     * @return 返回一个Mono对象，包含编码后的直接设备消息。
     */
    Mono<DirectDeviceMessage> encode(DeviceMessage message);

}

