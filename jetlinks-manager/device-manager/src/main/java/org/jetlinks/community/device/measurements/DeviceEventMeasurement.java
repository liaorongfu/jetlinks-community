package org.jetlinks.community.device.measurements;

import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.jetlinks.community.dashboard.*;
import org.jetlinks.community.dashboard.supports.StaticMeasurement;
import org.jetlinks.community.device.service.data.DeviceDataService;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.event.EventMessage;
import org.jetlinks.core.metadata.ConfigMetadata;
import org.jetlinks.core.metadata.DataType;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.EventMetadata;
import org.jetlinks.core.metadata.types.IntType;
import org.jetlinks.core.metadata.types.StringType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 设备事件测量类，继承自StaticMeasurement，用于处理设备事件的静态测量。
 */
class DeviceEventMeasurement extends StaticMeasurement {

    // 事件元数据，用于定义事件的测量维度和类型。
    public EventMetadata eventMetadata;

    // 事件总线，用于订阅和发布设备事件。
    public EventBus eventBus;

    // 设备数据服务，用于查询和处理设备数据。
    private final DeviceDataService deviceDataService;

    // 产品ID，用于标识设备所属的产品。
    private final String productId;

    /**
     * 构造函数，初始化DeviceEventMeasurement实例。
     *
     * @param productId 产品ID
     * @param eventBus 事件总线
     * @param eventMetadata 事件元数据
     * @param deviceDataService 设备数据服务
     */
    public DeviceEventMeasurement(String productId,
                                  EventBus eventBus,
                                  EventMetadata eventMetadata,
                                  DeviceDataService deviceDataService) {
        super(MetadataMeasurementDefinition.of(eventMetadata));
        this.productId = productId;
        this.eventBus = eventBus;
        this.eventMetadata = eventMetadata;
        this.deviceDataService = deviceDataService;
        addDimension(new RealTimeDeviceEventDimension());
    }

    // 配置元数据，用于配置设备事件测量的参数，如设备ID和历史数据量。
    static ConfigMetadata configMetadata = new DefaultConfigMetadata()
        .add("deviceId", "设备", "指定设备", new StringType().expand("selector", "device-selector"))
        .add("history", "历史数据量", "查询出历史数据后开始推送实时数据", new IntType().min(0).expand("defaultValue", 10));

    /**
     * 根据设备ID和指定的历史数据量查询历史测量值。
     *
     * 本方法通过构建查询参数，调用设备数据服务来获取指定设备的历史测量值。
     * 如果指定的历史数据量小于等于0，则不进行查询，直接返回空的Flux流。
     *
     * @param deviceId 设备唯一标识符，用于指定查询的设备。
     * @param history 指定查询的历史数据量，用于限定查询的结果数量。
     * @return 返回一个Flux流，包含查询到的历史测量值。
     */
    Flux<SimpleMeasurementValue> fromHistory(String deviceId, int history) {
        // 根据history的值判断是否进行查询
        return history <= 0 ? Flux.empty() : QueryParamEntity.newQuery()
            // 设置分页，从第0页开始，查询history数量的数据
            .doPaging(0, history)
            // 添加查询条件，筛选指定设备ID的数据
            .where("deviceId", deviceId)
            // 执行查询，调用设备数据服务查询历史事件
            .execute(q->deviceDataService.queryEvent(deviceId,eventMetadata.getId(),q,false))
            // 将查询结果映射为SimpleMeasurementValue对象，简化数据结构
            .map(data -> SimpleMeasurementValue.of(data, data.getTimestamp()))
            // 对查询结果进行排序，确保数据的时序性
            .sort(MeasurementValue.sort());
    }


    /**
     * 订阅指定设备的实时事件。
     *
     * @param deviceId 设备ID
     * @return 实时事件的Flux流
     */
    Flux<MeasurementValue> fromRealTime(String deviceId) {
        // 构建订阅对象，指定订阅的主题、资源路径和特性。
        Subscription subscription = Subscription
            .of("deviceEventMeasurement", "/device/" + productId + "/" + deviceId + "/message/event/" + eventMetadata.getId(), Subscription.Feature.local);
        // 订阅事件总线上的设备事件消息，转换为EventMessage类型，然后映射为SimpleMeasurementValue对象。
        return eventBus
            .subscribe(subscription, DeviceMessage.class)
            .cast(EventMessage.class)
            .map(msg -> SimpleMeasurementValue.of(msg.getData(), msg.getTimestamp()));
    }

    /**
     * 实时设备事件维度类，实现MeasurementDimension接口，用于处理实时设备事件的测量维度。
     */
    class RealTimeDeviceEventDimension implements MeasurementDimension {

        /**
         * 获取维度定义。
         *
         * @return 维度定义
         */
        @Override
        public DimensionDefinition getDefinition() {
            return CommonDimensionDefinition.realTime;
        }

        /**
         * 获取值的类型。
         *
         * @return 值的类型
         */
        @Override
        public DataType getValueType() {
            return eventMetadata.getType();
        }

        /**
         * 获取配置元数据。
         *
         * @return 配置元数据
         */
        @Override
        public ConfigMetadata getParams() {
            return configMetadata;
        }

        /**
         * 判断是否为实时测量。
         *
         * @return 是否为实时测量
         */
        @Override
        public boolean isRealTime() {
            return true;
        }

        /**
         * 根据测量参数获取测量值的流。
         *
         * 此方法首先从测量参数中提取设备ID，然后根据设备ID和参数中指定的历史记录数量，
         * 分别从历史数据源和实时数据源中获取测量值。如果历史记录数量未指定，则默认为0，
         * 即只获取实时测量值。
         *
         * @param parameter 测量参数，包含设备ID和要获取的历史记录数量等信息。
         * @return 包含测量值的Flux流，先返回历史测量值，后返回实时测量值。
         */
        @Override
        public Flux<MeasurementValue> getValue(MeasurementParameter parameter) {
            // 从参数中尝试获取设备ID，如果存在则返回Mono，否则返回空Mono。
            return Mono.justOrEmpty(parameter.getString("deviceId"))
                // 将设备ID转换为测量值的Flux流。
                .flatMapMany(deviceId -> {
                    // 从参数中尝试获取历史记录数量，如果未指定则默认为0。
                    int history = parameter.getInt("history").orElse(0);
                    // 合并历史测量值流和实时测量值流，先返回历史测量值，后返回实时测量值。
                    // 合并历史数据和实时数据的流
                    return Flux.concat(
                        fromHistory(deviceId, history),
                        fromRealTime(deviceId)
                    );
                });
        }

    }
}

