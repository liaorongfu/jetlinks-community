package org.jetlinks.community.gateway.supports;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.community.ValueObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备网关属性外观类
 * <p>
 * 转换设备网关属性数据
 * </p>
 *
 * @author zhouhao
 */
@Getter
@Setter
public class DeviceGatewayProperties implements ValueObject {

    private String id; // 网关ID
    private String name; // 网关名称
    private String description; // 网关描述
    private String provider; // 提供者信息
    private String channelId; // 通道ID
    private String protocol; // 通信协议
    private String transport; // 传输协议

    // 网关的配置信息，以键值对形式存储
    private Map<String,Object> configuration=new HashMap<>();

    /**
     * 获取配置值的映射对象。
     * @return 返回一个包含配置信息的Map对象。
     */
    @Override
    public Map<String, Object> values() {
        return configuration;
    }
}

