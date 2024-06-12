package org.jetlinks.community.device.measurements;

import lombok.Generated;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.jetlinks.community.dashboard.*;
import org.jetlinks.community.dashboard.supports.StaticMeasurement;
import org.jetlinks.community.device.service.data.DeviceDataService;
import org.jetlinks.community.gateway.DeviceMessageUtils;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.property.Property;
import org.jetlinks.core.metadata.*;
import org.jetlinks.core.metadata.types.NumberType;
import org.jetlinks.core.metadata.types.ObjectType;
import org.jetlinks.core.metadata.types.StringType;
import org.jetlinks.core.metadata.unit.ValueUnit;
import org.jetlinks.reactor.ql.utils.CastUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
/**
 * 设备属性测量类，继承自StaticMeasurement，用于处理设备属性的静态测量。
 * 该类主要负责从设备的历史属性数据和实时属性数据中生成测量值。
 */
@Slf4j
class DevicePropertiesMeasurement extends StaticMeasurement {

    /**
     * 事件总线，用于订阅和发布设备属性事件。
     */
    private final EventBus eventBus;

    /**
     * 设备数据服务，用于查询设备属性的历史数据。
     */
    private final DeviceDataService dataService;

    /**
     * 产品ID，用于标识设备所属的产品。
     */
    private final String productId;

    /**
     * 设备注册表，用于查询设备信息。
     */
    private final DeviceRegistry registry;

    /**
     * 构造函数，初始化DevicePropertiesMeasurement实例。
     *
     * @param productId        产品ID
     * @param eventBus         事件总线
     * @param dataService      设备数据服务
     * @param registry         设备注册表
     */
    public DevicePropertiesMeasurement(String productId,
                                       EventBus eventBus,
                                       DeviceDataService dataService,
                                       DeviceRegistry registry) {
        super(MeasurementDefinition.of("properties", "属性记录"));
        this.productId = productId;
        this.eventBus = eventBus;
        this.dataService = dataService;
        this.registry = registry;
        addDimension(new RealTimeDevicePropertyDimension());
        addDimension(new HistoryDevicePropertyDimension());
    }

    /**
     * 根据设备ID和历史记录数量，以及指定的属性集合，从历史数据中生成SimpleMeasurementValue的流。
     * 如果指定的历史记录数量小于等于0，则返回空流。
     *
     * @param deviceId 设备ID，用于查询指定设备的历史数据。
     * @param history 指定的历史记录数量，用于分页查询。
     * @param properties 属性集合，指定查询的属性。
     * @return 返回一个Flux流，包含从历史数据中查询到的SimpleMeasurementValue对象，按测量值排序。
     */
    Flux<SimpleMeasurementValue> fromHistory(String deviceId, int history, Set<String> properties) {
        // 如果历史记录数量小于等于0，返回空流
        return history <= 0
            ? Flux.empty()
            // 否则，构造查询参数实体，进行分页查询，并映射数据为SimpleMeasurementValue对象，最后进行排序
            : QueryParamEntity
                .newQuery()
                .doPaging(0, history)
                .execute(q -> dataService.queryEachProperties(deviceId, q, properties.toArray(new String[0])))
                .map(data -> SimpleMeasurementValue.of(data, data.getTimestamp()))
                .sort(MeasurementValue.sort());
    }

    /**
     * 根据设备元数据和属性信息创建一个包含属性值和相关信息的Map。
     *
     * @param metadata 设备的元数据，包含属性的详细定义。
     * @param property 设备的属性，包含属性的当前值和状态。
     * @return 一个Map，包含属性的格式化值、实际值、状态、属性ID、时间戳和单位（如果适用）。
     */
    Map<String, Object> createValue(DeviceMetadata metadata, Property property) {
        // 尝试根据属性ID获取属性的元数据
        return metadata
            .getProperty(property.getId())
            .map(meta -> {
                Map<String, Object> values = new HashMap<>();
                DataType type = meta.getValueType();
                Object val;

                // 根据属性的类型，对值进行相应的处理
                if (type instanceof NumberType) {
                    NumberType<?> numberType = ((NumberType<?>) type);
                    // 对数字类型的属性值进行精度处理
                    val = NumberType.convertScaleNumber(property.getValue(), numberType.getScale(), numberType.getRound(), Function.identity());
                } else if (type instanceof Converter) {
                    // 使用转换器将属性值转换为特定类型
                    val = ((Converter<?>) type).convert(property.getValue());
                } else {
                    // 对于其他类型，直接使用原始值
                    val = property.getValue();
                }

                // 根据属性的类型，添加格式化后的值、实际值、状态、属性ID和时间戳到Map中
                values.put("formatValue", type.format(val));
                values.put("value", val);
                values.put("state", property.getState());
                values.put("property", property.getId());
                values.put("timestamp",property.getTimestamp());

                // 如果类型支持单位，添加单位到Map中
                if (type instanceof UnitSupported) {
                    UnitSupported unitSupported = (UnitSupported) type;
                    values.put("unit", Optional.ofNullable(unitSupported.getUnit())
                                               .map(ValueUnit::getSymbol)
                                               .orElse(null));
                }
                return values;
            })
            .orElseGet(() -> {
                // 如果属性元数据不存在，创建一个包含基本值和信息的Map
                Map<String, Object> values = new HashMap<>();
                values.put("formatValue", property.getValue());
                values.put("value", property.getValue());
                values.put("state", property.getState());
                values.put("property", property.getId());
                values.put("timestamp",property.getTimestamp());
                return values;
            });
    }

    static Subscription.Feature[] clusterFeature = {Subscription.Feature.local, Subscription.Feature.broker};
    static Subscription.Feature[] nonClusterFeature = {Subscription.Feature.local};


    /**
     * 根据指定的设备ID、属性集合和集群状态，从实时数据流中转换并返回MeasurementValue的Flux流。
     * 这个方法用于订阅设备的实时属性报告和回复消息，然后根据指定的属性ID过滤和转换消息。
     *
     * @param deviceId 设备的唯一标识符。
     * @param properties 需要订阅的属性ID集合。如果为空，则订阅所有属性。
     * @param cluster 指示是否在集群模式下订阅。
     * @return 返回一个包含MeasurementValue对象的Flux流，这些对象是根据订阅的设备属性消息转换而来的。
     */
    Flux<MeasurementValue> fromRealTime(String deviceId, Set<String> properties, boolean cluster) {
        // 创建订阅配置，指定订阅的主题和功能特征
        Subscription subscription = Subscription.of(
            "realtime-device-properties-measurement",
            new String[]{
                "/device/" + productId + "/" + deviceId + "/message/property/report",
                "/device/" + productId + "/" + deviceId + "/message/property/*/reply"
            },
            cluster ? clusterFeature : nonClusterFeature
        );

        // 从设备注册表中获取指定设备，然后获取其元数据
        return registry
            .getDevice(deviceId)
            .flatMap(DeviceOperator::getMetadata)
            .flatMapMany(metadata -> {
                List<PropertyMetadata> props = metadata.getProperties();
                Map<String, Integer> index = new HashMap<>();
                int idx = 0;
                // 构建属性ID到索引的映射，用于后续排序
                for (PropertyMetadata prop : props) {
                    if (properties.isEmpty() || properties.contains(prop.getId())) {
                        index.put(prop.getId(), idx++);
                    }
                }

                // 订阅事件总线上的设备消息，然后筛选和转换为MeasurementValue
                return
                    eventBus
                        .subscribe(subscription, DeviceMessage.class)
                        .flatMap(msg -> Flux
                            .fromIterable(DeviceMessageUtils.tryGetCompleteProperties(msg))
                            .filter(e -> index.containsKey(e.getId()))
                            // 对属性进行排序
                            //对本次上报的属性进行排序
                            .sort(Comparator.comparingInt(e -> index.getOrDefault(e.getId(), 0)))
                            .<MeasurementValue>map(e -> SimpleMeasurementValue.of(createValue(metadata, e), e.getTimestamp())))
                        .onErrorContinue((err, v) -> log.error(err.getMessage(), err))
                ;
            });
    }

    static ConfigMetadata configMetadata = new DefaultConfigMetadata()
        .add("deviceId", "设备", "指定设备", new StringType().expand("selector", "device-selector"));

    /**
     * 从MeasurementParameter对象中获取属性名称的集合。
     *
     * 此方法旨在处理MeasurementParameter对象中可能包含的属性字段。该字段是一个字符串数组，
     * 本方法将其转换为字符串集合，以便更方便地进行后续处理或查询。
     * 如果参数中没有“properties”字段或该字段为空，方法将返回一个空的集合。
     *
     * @param parameter MeasurementParameter对象，其中可能包含属性字段。
     * @return 属性名称的集合，如果不存在属性字段，则返回空集合。
     */
    static Set<String> getPropertiesFromParameter(MeasurementParameter parameter) {
        // 通过get方法尝试获取"properties"字段，然后使用map方法将其转换为字符串数组
        return parameter
            .get("properties")
            // 如果"properties"不存在或为空，则使用orElse方法返回一个空列表
            .map(CastUtils::castArray)
            .orElse(Collections.emptyList())
            // 将字符串数组中的每个元素转换为字符串
            .stream()
            .map(String::valueOf)
            // 最终将所有字符串收集到一个集合中
            .collect(Collectors.toSet());
    }


    /**
     * 历史
     */
    private class HistoryDevicePropertyDimension implements MeasurementDimension {

        @Override
        @Generated
        public DimensionDefinition getDefinition() {
            return CommonDimensionDefinition.history;
        }

        @Override
        @Generated
        public DataType getValueType() {
            return new ObjectType()
                .addProperty("property", "属性", StringType.GLOBAL)
                .addProperty("value", "值", StringType.GLOBAL)
                .addProperty("formatValue", "格式化值", StringType.GLOBAL);
        }

        @Override
        @Generated
        public ConfigMetadata getParams() {
            return configMetadata;
        }

        @Override
        @Generated
        public boolean isRealTime() {
            return false;
        }

        /**
         * 根据测量参数获取测量值的流。
         *
         * @param parameter 测量参数，包含设备ID和历史数据查询数量等信息。
         * @return 返回一个测量值的Flux流，可以从多个设备的历史数据中获取。
         */
        @Override
        public Flux<MeasurementValue> getValue(MeasurementParameter parameter) {
            // 尝试从参数中获取设备ID，如果存在则使用，否则返回一个空的Mono。
            return Mono
                .justOrEmpty(parameter.getString("deviceId"))
                // 根据设备ID和参数中指定的历史数据数量，以及额外的属性，从历史数据中获取测量值。
                .flatMapMany(deviceId -> {
                    // 从参数中获取历史数据的数量，如果未指定则默认为1。
                    int history = parameter.getInt("history").orElse(1);

                    // 根据设备ID、历史数据数量和属性，转换为测量值的Flux流。
                    return fromHistory(deviceId, history, getPropertiesFromParameter(parameter));
                });
        }

    }

    /**
     * 实时
     */
    private class RealTimeDevicePropertyDimension implements MeasurementDimension {

        @Override
        @Generated
        public DimensionDefinition getDefinition() {
            return CommonDimensionDefinition.realTime;
        }

        @Override
        @Generated
        public DataType getValueType() {
            return new ObjectType()
                .addProperty("property", "属性", StringType.GLOBAL)
                .addProperty("value", "值", StringType.GLOBAL)
                .addProperty("formatValue", "格式化值", StringType.GLOBAL);
        }

        @Override
        @Generated
        public ConfigMetadata getParams() {
            return configMetadata;
        }

        @Override
        @Generated
        public boolean isRealTime() {
            return true;
        }

        /**
         * 根据测量参数获取测量值。
         *
         * @param parameter 测量参数，包含设备标识符和查询历史数据的数量等信息。
         * @return 测量值的流，包括历史测量值和实时测量值。
         */
        @Override
        public Flux<MeasurementValue> getValue(MeasurementParameter parameter) {
            // 从参数中尝试获取设备ID，如果存在则继续处理，否则返回空的Flux。
            return Mono
                .justOrEmpty(parameter.getString("deviceId"))
                .flatMapMany(deviceId -> {
                    // 从参数中获取历史数据查询数量，若未指定则默认为0。
                    int history = parameter.getInt("history").orElse(0);
                    // 合并历史测量值流和实时测量值流，形成一个包含历史和实时数据的测量值流。
                    // 合并历史数据和实时数据
                    return Flux.concat(
                        // 根据设备ID和查询历史数量，以及从参数中提取的其他属性，查询历史测量值。
                        // 查询历史数据
                        fromHistory(deviceId, history, getPropertiesFromParameter(parameter))
                        ,
                        // 根据设备ID和从参数中提取的其他属性，订阅并获取实时测量值。
                        // 如果参数中未指定集群模式，默认为true。
                        // 从消息网关订阅实时事件消息
                        fromRealTime(deviceId, getPropertiesFromParameter(parameter), parameter.getBoolean("cluster", true))
                    );
                });
        }

    }
}
