package org.jetlinks.community.configure.device;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Generated;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.codec.binary.Base64;
import org.hswebframework.ezorm.rdb.mapping.annotation.ColumnType;
import org.hswebframework.web.api.crud.entity.GenericEntity;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.server.session.DeviceSessionProvider;
import org.jetlinks.core.server.session.PersistentSession;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import javax.persistence.Column;
import java.sql.JDBCType;

@Getter
@Setter
@Generated
/**
 * PersistentSessionEntity类扩展了GenericEntity类，用于表示一个持久化的会话实体。
 * 它包含了与设备会话相关的信息，例如设备会话提供商、连接的网关服务ID、设备ID等。
 */
public class PersistentSessionEntity extends GenericEntity<String> {

    /**
     * 设备会话提供商的ID。这个属性是用来标识提供设备会话服务的实体。
     * 其值由设备会话提供商提供。
     */
    @Schema(description="设备会话提供商")
    @Column(length = 32, nullable = false)
    private String provider;

    /**
     * 设备连接的网关服务ID。这个属性标识了设备是通过哪个网关服务进行连接的。
     */
    @Schema(description="设备连接的网关服务ID")
    @Column(length = 64, nullable = false)
    private String serverId;

    /**
     * 设备ID。这个属性唯一标识了一个设备。
     */
    @Schema(description="设备ID")
    @Column(length = 64, nullable = false)
    private String deviceId;

    /**
     * 会话超时时间。这个属性表示一个会话在没有活动时保持有效的时间长度。
     */
    @Schema(description="会话超时时间")
    @Column
    private Long keepAliveTimeout;

    /**
     * 最近会话时间。这个属性记录了最后一次会话活动的时间。
     */
    @Schema(description="最近会话时间")
    @Column
    private Long lastKeepAliveTime;

    /**
     * 会话序列化数据。这个属性存储了会话的序列化表示，用于在需要时反序列化为会话对象。
     */
    @Schema(description="会话序列化")
    @Column
    @ColumnType(javaType = String.class, jdbcType = JDBCType.LONGVARCHAR)
    private String sessionBase64;

    /**
     * 从给定的参数创建一个PersistentSessionEntity实例。
     *
     * @param serverId 设备连接的网关服务ID
     * @param session 表示一个持久化会话的对象
     * @param registry 设备注册信息
     * @return PersistentSessionEntity的Mono实例
     */
    public static Mono<PersistentSessionEntity> from(String serverId,
                                                     PersistentSession session,
                                                     DeviceRegistry registry) {
        PersistentSessionEntity entity = new PersistentSessionEntity();

        // 初始化实体属性
        entity.setId(session.getId());
        entity.setProvider(session.getProvider());
        entity.setServerId(serverId);
        entity.setDeviceId(session.getDeviceId());
        entity.setKeepAliveTimeout(session.getKeepAliveTimeout().toMillis());
        entity.setLastKeepAliveTime(session.lastPingTime());

        // 查找设备会话提供商
        DeviceSessionProvider provider = DeviceSessionProvider
            .lookup(session.getProvider())
            .orElseGet(UnknownDeviceSessionProvider::getInstance);

        // 序列化会话并设置到实体中
        return provider
            .serialize(session, registry)
            .map(Base64::encodeBase64String)
            .doOnNext(entity::setSessionBase64)
            .thenReturn(entity);

    }

    /**
     * 将当前实体转换为PersistentSession对象。
     *
     * @param registry 设备注册信息
     * @return PersistentSession的Mono实例，如果无法反序列化则返回空的Mono
     */
    public Mono<PersistentSession> toSession(DeviceRegistry registry) {
        // 查找设备会话提供商
        DeviceSessionProvider provider = DeviceSessionProvider
            .lookup(getProvider())
            .orElseGet(UnknownDeviceSessionProvider::getInstance);

        // 如果存在序列化的会话数据，尝试反序列化为会话对象
        if (StringUtils.hasText(sessionBase64)) {
            return provider.deserialize(Base64.decodeBase64(sessionBase64), registry);
        }
        return Mono.empty();
    }
}

