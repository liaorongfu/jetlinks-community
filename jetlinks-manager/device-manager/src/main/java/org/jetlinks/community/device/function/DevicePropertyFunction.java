package org.jetlinks.community.device.function;

import org.jetlinks.core.message.DeviceDataManager;
import org.jetlinks.reactor.ql.supports.map.FunctionMapFeature;
import org.jetlinks.reactor.ql.utils.CastUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 在reactorQL中获取设备最新属性
 * <pre>{@code
 * select device.property.recent(deviceId,'temperature',timestamp) recent from ...
 *
 * select * from ... where device.property.recent(deviceId,'temperature',timestamp)  = 'xxx'
 * }</pre>
 *
 * 扩展FunctionMapFeature类，实现根据设备ID和属性ID获取最近的设备属性值的功能。
 * 该功能用于从设备数据管理器中检索指定设备和属性的最近属性值。
 * @author zhouhao
 * @since 2.0
 */
@Component
public class DevicePropertyFunction extends FunctionMapFeature {
    /**
     * 构造函数初始化DevicePropertyFunction。
     *
     * @param dataManager 设备数据管理器，用于访问和管理设备属性数据。
     */
    public DevicePropertyFunction(DeviceDataManager dataManager) {
        super("device.property.recent", 3, 2, flux -> flux
            .collectList()
            .flatMap(args -> {
                // 检查参数数量，少于两个参数则不处理
                if (args.size() < 2) {
                    return Mono.empty();
                }
                // 提取设备ID和属性ID
                String deviceId = String.valueOf(args.get(0));
                String property = String.valueOf(args.get(1));
                // 如果有第三个参数，则作为时间戳，否则使用当前时间戳
                long timestamp = args.size() > 2
                    ? CastUtils.castNumber(args.get(2))
                               .longValue()
                    : System.currentTimeMillis();

                // 从设备数据管理器中获取最近的属性值，并返回该值
                return dataManager
                    .getLastProperty(deviceId, property, timestamp)
                    .map(DeviceDataManager.PropertyValue::getValue);
            }));
    }
}
