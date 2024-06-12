package org.jetlinks.community.device.function;

import lombok.AllArgsConstructor;
import org.hswebframework.ezorm.core.NestConditional;
import org.hswebframework.ezorm.rdb.mapping.ReactiveQuery;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.community.device.entity.DeviceInstanceEntity;
import org.jetlinks.community.rule.engine.executor.DeviceSelector;
import org.jetlinks.community.rule.engine.executor.DeviceSelectorBuilder;
import org.jetlinks.community.rule.engine.executor.device.DeviceSelectorProvider;
import org.jetlinks.community.rule.engine.executor.device.DeviceSelectorProviders;
import org.jetlinks.community.rule.engine.executor.device.DeviceSelectorSpec;
import org.jetlinks.reactor.ql.ReactorQL;
import org.jetlinks.reactor.ql.ReactorQLContext;
import org.jetlinks.reactor.ql.ReactorQLRecord;
import org.jetlinks.reactor.ql.feature.FromFeature;
import org.springframework.data.util.Lazy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * * 设备管理器类，负责管理设备的注册和查询。
 *  * 该类使用Reactive Repository模式来异步处理设备数据的存取。
 * 基于ReactorQL的设备选择器,通过自定义{@link FromFeature}来实现设备数据源.
 * <pre>
 * in_gourp('groupId') 在指定的设备分组中
 * in_group_tree('groupId') 在指定分组中（包含下级分组）
 * same_group('deviceId') 在指定设备的相同分组中
 * product('productId') 指定产品ID对应的设备
 * tag('tag1Key','tag1Value','tag2Key','tag2Value') 按指定的标签获取
 * state('online') 按指定的状态获取
 * in_tenant('租户ID') 在指定租户中的设备
 * </pre>
 *
 * @author zhouhao
 * @since 2.0
 */
@AllArgsConstructor
public class ReactorQLDeviceSelectorBuilder implements DeviceSelectorBuilder {

    /**
     * 设备注册信息的存储仓库。
     * 通过这个仓库，可以进行设备注册信息的查询和更新操作。
     */
    private final DeviceRegistry registry;

    /**
     * 设备实例的存储仓库。
     * 该仓库用于存储和检索设备实例的信息，基于Reactive模式提供异步数据访问。
     */
    private final ReactiveRepository<DeviceInstanceEntity, String> deviceRepository;



    /**
     * 根据设备选择器规范创建设备选择器。
     *
     * @param spec 设备选择器规范，定义了如何选择设备的规则。
     * @return 返回一个设备选择器，该选择器可以根据指定的规则选择设备。
     * @throws UnsupportedOperationException 如果指定的选择器不受支持，则抛出此异常。
     */
    @Override
    @SuppressWarnings("all")
    public DeviceSelector createSelector(DeviceSelectorSpec spec) {
        // 根据规范获取选择器提供者，并确保提供者存在，否则抛出异常。
        DeviceSelectorProvider provider = DeviceSelectorProviders
            .getProvider(spec.getSelector())
            .orElseThrow(() -> new UnsupportedOperationException("unsupported selector:" + spec.getSelector()));

        // 如果是固定设备选择器，则直接返回一个根据规范解析设备的函数。
        // 固定设备,直接获取,避免查询数据库性能低.
        if (DeviceSelectorProviders.isFixed(spec)) {
            return ctx -> {
                return spec
                    .resolveSelectorValues(ctx)
                    .map(String::valueOf)
                    .flatMap(registry::getDevice);
            };
        }

        // 获取延迟加载函数，该函数根据规范和上下文创建设备查询并执行选择。
        BiFunction<NestConditional<ReactiveQuery<DeviceInstanceEntity>>,
            Map<String, Object>,
            Mono<NestConditional<ReactiveQuery<DeviceInstanceEntity>>>> function = provider
            .createLazy(spec);

        // 返回一个函数，该函数使用延迟加载函数和上下文来创建设备查询，
        // 然后根据查询结果获取设备ID，并通过注册表获取实际设备。
        return context -> function
            .apply(deviceRepository
                       .createQuery()
                       .select(DeviceInstanceEntity::getId)
                       .nest(),
                   context
            )
            .flatMapMany(ctd -> ctd.end().fetch().map(DeviceInstanceEntity::getId))
            .flatMap(registry::getDevice);
    }


    /**
     * ReactorQLDeviceSelector类是一个实现了DeviceSelector接口的静态类。
     * 它的作用是根据提供的上下文信息，使用ReactorQL查询语言来选择相应的设备。
     *
     * @param ql ReactorQL实例，用于执行查询语言指令。
     * @param registry 设备注册表，用于根据查询结果获取具体的设备。
     */
    @AllArgsConstructor
    static class ReactorQLDeviceSelector implements DeviceSelector {

        /**
         * ReactorQL实例，用于执行查询语言操作。
         */
        private final ReactorQL ql;

        /**
         * 设备注册表，存储和管理所有设备的信息。
         */
        private final DeviceRegistry registry;

        /**
         * 根据提供的上下文信息选择设备。
         *
         * @param context 上下文信息，包含查询所需的键值对数据。
         * @return 返回一个Flux<DeviceOperator>，表示一个设备操作的流。
         *         该方法首先使用ReactorQL查询语言处理上下文信息，然后根据查询结果从设备注册表中获取相应的设备。
         */
        @Override
        public Flux<DeviceOperator> select(Map<String, Object> context) {
            // 使用ReactorQL查询语言启动查询，上下文数据通过Flux.just(context)提供。
            // 然后将查询结果映射为Map格式，最后通过设备注册表获取具体的设备。
            return ql
                .start(
                    ReactorQLContext
                        .ofDatasource((r) -> Flux.just(context))
                        .bindAll(context)
                )
                .map(ReactorQLRecord::asMap)
                .flatMap(res -> registry.getDevice((String) res.get("id")))
                ;
        }
    }
}
