package org.jetlinks.community.configure.device;

import lombok.Generated;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.server.session.DeviceSessionProvider;
import org.jetlinks.core.server.session.PersistentSession;
import reactor.core.publisher.Mono;

/**
 * 生成的类，用于提供未知设备的会话管理。
 */
@Generated
public class UnknownDeviceSessionProvider implements DeviceSessionProvider {

    // 单例实例，用于全局访问UnknownDeviceSessionProvider
    public static UnknownDeviceSessionProvider instance = new UnknownDeviceSessionProvider();

    /**
     * 获取UnknownDeviceSessionProvider的单例实例。
     *
     * @return UnknownDeviceSessionProvider的单例实例。
     */
    public static UnknownDeviceSessionProvider getInstance() {
        return instance;
    }

    /**
     * 获取设备会话的ID。
     *
     * @return 字符串ID，对于未知设备始终返回"unknown"。
     */
    @Override
    public String getId() {
        return "unknown";
    }

    /**
     * 反序列化设备会话数据。
     * 对于未知设备，此方法不执行任何操作，直接返回空的Mono对象。
     *
     * @param sessionData 设备会话的字节数据。
     * @param registry 设备注册信息。
     * @return 一个空的Mono对象，表示无法反序列化未知设备的会话数据。
     */
    @Override
    public Mono<PersistentSession> deserialize(byte[] sessionData, DeviceRegistry registry) {
        return Mono.empty();
    }

    /**
     * 序列化设备会话。
     * 对于未知设备，此方法不执行任何操作，直接返回空的Mono对象。
     *
     * @param session 设备会话实例。
     * @param registry 设备注册信息。
     * @return 一个空的Mono对象，表示无法序列化未知设备的会话数据。
     */
    @Override
    public Mono<byte[]> serialize(PersistentSession session, DeviceRegistry registry) {
        return Mono.empty();
    }
}

