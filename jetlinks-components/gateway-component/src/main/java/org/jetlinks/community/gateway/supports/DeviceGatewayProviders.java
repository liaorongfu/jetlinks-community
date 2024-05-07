package org.jetlinks.community.gateway.supports;

import org.hswebframework.web.exception.I18nSupportException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备网关提供者管理类，用于注册、获取和列出设备网关提供者。
 */
public class DeviceGatewayProviders {
    // 存储设备网关提供者的映射，线程安全
    private static final Map<String, DeviceGatewayProvider> providers = new ConcurrentHashMap<>();

    /**
     * 注册一个设备网关提供者。
     *
     * @param provider 要注册的设备网关提供者实例
     */
    public static void register(DeviceGatewayProvider provider) {
        providers.put(provider.getId(), provider);
    }

    /**
     * 根据提供者ID获取设备网关提供者，可能为空。
     *
     * @param provider 提供者的ID
     * @return Optional包装的设备网关提供者实例，可能为空
     */
    public static Optional<DeviceGatewayProvider> getProvider(String provider) {
        return Optional.ofNullable(providers.get(provider));
    }

    /**
     * 根据提供者ID强制获取设备网关提供者，不存在时抛出异常。
     *
     * @param provider 提供者的ID
     * @return 设备网关提供者实例
     * @throws I18nSupportException 如果指定的设备网关提供者不存在
     */
    public static DeviceGatewayProvider getProviderNow(String provider) {
        DeviceGatewayProvider gatewayProvider = providers.get(provider);
        if (null == gatewayProvider) {
            throw new I18nSupportException("error.unsupported_device_gateway_provider", provider);
        }
        return gatewayProvider;
    }

    /**
     * 获取所有已注册的设备网关提供者列表。
     *
     * @return 设备网关提供者列表
     */
    public static List<DeviceGatewayProvider> getAll() {
        return new ArrayList<>(providers.values());
    }

}

