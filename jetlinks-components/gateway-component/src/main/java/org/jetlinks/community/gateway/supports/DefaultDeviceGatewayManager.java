package org.jetlinks.community.gateway.supports;

import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.gateway.DeviceGateway;
import org.jetlinks.community.gateway.DeviceGatewayManager;
import org.jetlinks.community.network.channel.ChannelInfo;
import org.jetlinks.community.network.channel.ChannelProvider;
import org.jetlinks.core.cache.ReactiveCacheContainer;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认设备网关管理器实现，负责管理设备网关的生命周期和属性。
 */
@Slf4j
public class DefaultDeviceGatewayManager implements DeviceGatewayManager {

    // 设备网关属性管理器
    private final DeviceGatewayPropertiesManager propertiesManager;

    // 提供者映射，存储设备网关提供者
    private final Map<String, DeviceGatewayProvider> providers = new ConcurrentHashMap<>();

    // 设备网关存储容器，使用响应式缓存
    private final ReactiveCacheContainer<String, DeviceGateway> store = ReactiveCacheContainer.create();

    // 通道提供者映射，存储通道提供者
    private final Map<String, ChannelProvider> channels = new ConcurrentHashMap<>();

    /**
     * 添加通道提供者。
     *
     * @param provider 通道提供者实例
     */
    public void addChannelProvider(ChannelProvider provider) {
        channels.put(provider.getChannel(), provider);
    }

    /**
     * 添加设备网关提供者。
     *
     * @param provider 设备网关提供者实例
     */
    public void addGatewayProvider(DeviceGatewayProvider provider) {
        providers.put(provider.getId(), provider);
    }

    /**
     * 构造函数，初始化设备网关管理器。
     *
     * @param propertiesManager 设备网关属性管理器
     */
    public DefaultDeviceGatewayManager(DeviceGatewayPropertiesManager propertiesManager) {
        this.propertiesManager = propertiesManager;
    }

    /**
     * 私有方法，获取指定ID的设备网关。
     *
     * @param id 设备网关ID
     * @return 设备网关的Mono实例
     */
    private Mono<DeviceGateway> doGetGateway(String id) {
        if (null == id) {
            return Mono.empty();
        }
        return store
            .computeIfAbsent(id, this::createGateway);
    }

    /**
     * 创建设备网关。
     *
     * @param id 设备网关ID
     * @return 设备网关的Mono实例
     */
    protected Mono<DeviceGateway> createGateway(String id) {
        return propertiesManager
            .getProperties(id)
            .switchIfEmpty(Mono.error(() -> new UnsupportedOperationException("网关配置[" + id + "]不存在")))
            .flatMap(properties -> getProviderNow(properties.getProvider()).createDeviceGateway(properties));
    }

    /**
     * 关闭指定ID的设备网关。
     *
     * @param gatewayId 设备网关ID
     * @return 空的Mono实例
     */
    @Override
    public Mono<Void> shutdown(String gatewayId) {
        return doShutdown(gatewayId);
    }

    /**
     * 私有方法，实际关闭操作。
     *
     * @param gatewayId 设备网关ID
     * @return 空的Mono实例
     */
    public Mono<Void> doShutdown(String gatewayId) {
        return Mono.justOrEmpty(store.remove(gatewayId))
                   .flatMap(DeviceGateway::shutdown)
                   .doOnSuccess(nil -> log.debug("shutdown device gateway {}", gatewayId))
                   .doOnError(err -> log.error("shutdown device gateway {} error", gatewayId, err));
    }

    /**
     * 启动指定ID的设备网关。
     *
     * @param gatewayId 设备网关ID
     * @return 空的Mono实例
     */
    @Override
    public Mono<Void> start(String gatewayId) {
        return this
            .doStart(gatewayId);
    }

    /**
     * 私有方法，实际启动操作。
     *
     * @param id 设备网关ID
     * @return 空的Mono实例
     */
    public Mono<Void> doStart(String id) {
        return this
            .getGateway(id)
            .flatMap(DeviceGateway::startup)
            .doOnSuccess(nil -> log.debug("started device gateway {}", id))
            .doOnError(err -> log.error("start device gateway {} error", id, err));
    }

    /**
     * 获取指定ID的设备网关。
     *
     * @param id 设备网关ID
     * @return 设备网关的Mono实例
     */
    @Override
    public Mono<DeviceGateway> getGateway(String id) {
        return doGetGateway(id);
    }

    /**
     * 重新加载指定ID的设备网关配置。
     *
     * @param gatewayId 设备网关ID
     * @return 空的Mono实例
     */
    @Override
    public Mono<Void> reload(String gatewayId) {
        return this
            .doReload(gatewayId);
    }

    /**
     * 私有方法，实际重新加载操作。
     *
     * @param gatewayId 设备网关ID
     * @return 空的Mono实例
     */
    private Mono<Void> doReload(String gatewayId) {
        return propertiesManager
            .getProperties(gatewayId)
            .flatMap(prop -> {

                DeviceGatewayProvider provider = this.getProviderNow(prop.getProvider());
                return store
                    .compute(gatewayId, (id, gateway) -> {
                        if (gateway != null) {
                            log.debug("reload device gateway {} {}:{}", prop.getName(), prop.getProvider(), prop.getId());
                            return provider
                                .reloadDeviceGateway(gateway, prop)
                                .cast(DeviceGateway.class);
                        }
                        log.debug("create device gateway {} {}:{}", prop.getName(), prop.getProvider(), prop.getId());
                        return provider
                            .createDeviceGateway(prop)
                            .flatMap(newer -> newer.startup().thenReturn(newer));
                    });
            })
            .then();
    }

    /**
     * 获取所有设备网关提供者列表。
     *
     * @return 设备网关提供者列表
     */
    @Override
    public List<DeviceGatewayProvider> getProviders() {
        return providers
            .values()
            .stream()
            .sorted(Comparator.comparingInt(DeviceGatewayProvider::getOrder))
            .collect(Collectors.toList());
    }

    /**
     * 获取指定ID的设备网关提供者。
     *
     * @param provider 设备网关提供者ID
     * @return 设备网关提供者的Optional实例
     */
    @Override
    public Optional<DeviceGatewayProvider> getProvider(String provider) {
        return Optional.ofNullable(providers.get(provider));
    }

    /**
     * 立即获取指定名称的设备网关提供者。
     *
     * @param provider 设备网关提供者名称
     * @return 设备网关提供者实例
     */
    public DeviceGatewayProvider getProviderNow(String provider) {
        return DeviceGatewayProviders.getProviderNow(provider);
    }

    /**
     * 获取指定通道和通道ID的信息。
     *
     * @param channel 通道名称
     * @param channelId 通道ID
     * @return 通道信息的Mono实例
     */
    @Override
    public Mono<ChannelInfo> getChannel(String channel, String channelId) {
        if (!StringUtils.hasText(channel) || !StringUtils.hasText(channel)) {
            return Mono.empty();
        }
        return Mono.justOrEmpty(channels.get(channel))
                   .flatMap(provider -> provider.getChannelInfo(channelId));
    }

}

