package org.jetlinks.community.device.measurements;

import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.jetlinks.community.dashboard.*;
import org.jetlinks.community.dashboard.supports.StaticMeasurement;
import org.jetlinks.community.device.service.data.DeviceDataService;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.event.EventMessage;
import org.jetlinks.core.metadata.*;
import org.jetlinks.core.metadata.types.ObjectType;
import org.jetlinks.core.metadata.types.StringType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备事件测量类，继承自StaticMeasurement，用于处理设备的事件测量数据。
 * 该类提供了从历史记录和实时订阅中获取测量值的功能。
 */
class DeviceEventsMeasurement extends StaticMeasurement {

    // 事件总线，用于订阅和发布事件。
    private final EventBus eventBus;
    // 设备数据服务，用于查询设备的详细数据。
    private final DeviceDataService deviceDataService;
    // 设备元数据，包含设备的事件定义等信息。
    private final DeviceMetadata metadata;
    // 产品ID，用于标识设备所属的产品。
    private final String productId;

    /**
     * 构造函数，初始化DeviceEventsMeasurement实例。
     *
     * @param productId 产品ID。
     * @param eventBus 事件总线。
     * @param deviceMetadata 设备元数据。
     * @param deviceDataService 设备数据服务。
     */
    public DeviceEventsMeasurement(String productId,
                                   EventBus eventBus,
                                   DeviceMetadata deviceMetadata,
                                   DeviceDataService deviceDataService) {
        super(MeasurementDefinition.of("events", "事件记录"));
        this.productId = productId;
        this.eventBus = eventBus;
        this.deviceDataService = deviceDataService;
        this.metadata = deviceMetadata;
        addDimension(new RealTimeDevicePropertyDimension());
    }

    // 历史事件数量的原子长整型变量，用于统计。
    static AtomicLong num = new AtomicLong();

    /**
     * 根据设备ID和历史记录数量，从历史记录中生成SimpleMeasurementValue的Flux流。
     * 如果指定的历史记录数量小于等于0，则返回一个空的Flux流。
     *
     * @param deviceId 设备ID，用于查询特定设备的历史记录。
     * @param history 指定的历史记录数量，用于限定查询的数量。
     * @return 返回一个Flux流，包含从历史记录中查询到的SimpleMeasurementValue对象，按时间排序。
     */
    Flux<SimpleMeasurementValue> fromHistory(String deviceId, int history) {
        // 如果历史记录数量小于等于0，返回空的Flux流
        return history <= 0 ? Flux.empty() : Flux.fromIterable(metadata.getEvents())
            .flatMap(event -> QueryParamEntity.newQuery()
                // 设置分页，查询历史记录的第0页，查询数量为指定的历史记录数量
                .doPaging(0, history)
                // 执行查询，获取设备的历史事件数据
                .execute(q -> deviceDataService.queryEvent(deviceId, event.getId(), q, false))
                // 将查询结果转换为SimpleMeasurementValue对象
                .map(data -> SimpleMeasurementValue.of(createValue(event.getId(), data), data.getTimestamp()))
                // 对转换后的SimpleMeasurementValue对象按时间进行排序
                .sort(MeasurementValue.sort()));
    }

    /**
     * 创建一个包含事件和数据的Map对象。
     *
     * 此方法用于封装事件和其相关数据到一个Map中，以便于后续处理或传输。
     * 通过将事件名称和数据封装在一起，可以方便地将它们作为单个单元处理，
     * 这对于需要同时传递多个信息的情况非常有用。
     *
     * @param event 事件名称，用于标识发生了什么事件。
     * @param value 与事件相关联的数据，可以是任何类型的对象。
     * @return 包含事件名称和数据的Map对象。
     */
    Map<String, Object> createValue(String event, Object value) {
        // 初始化一个空的Map来存储事件和数据
        Map<String, Object> values = new HashMap<>();
        // 将事件名称和数据添加到Map中
        values.put("event", event);
        values.put("data", value);
        // 返回包含事件和数据的Map
        return values;
    }


    /**
     * 根据指定的设备ID，订阅实时测量值。
     *
     * 通过订阅与产品和设备相关联的事件消息，来获取设备的实时测量值。
     * 订阅的主题格式为 "/device/{productId}/{deviceId}/message/event/*"，使用本地和代理功能。
     *
     * @param deviceId 设备的唯一标识符，用于指定订阅的设备。
     * @return 返回一个Flux流，该流包含从设备实时事件中提取的测量值。
     */
    Flux<MeasurementValue> fromRealTime(String deviceId) {
        // 创建订阅对象，用于订阅设备的实时事件消息
        Subscription subscription = Subscription.of(
            "realtime-device-events-measurement",
            "/device/" + productId + "/" + deviceId + "/message/event/*",
            Subscription.Feature.local, Subscription.Feature.broker
        );

        // 订阅事件总线上的设备消息，并进行一系列处理
        return
            eventBus
                .subscribe(subscription, DeviceMessage.class) // 订阅指定的主题并接收DeviceMessage类型的消息
                .filter(EventMessage.class::isInstance) // 过滤出实现了EventMessage接口的消息
                .cast(EventMessage.class) // 将过滤后的消息转换为EventMessage类型
                .map(kv -> SimpleMeasurementValue.of(createValue(kv.getEvent(), kv.getData()), System.currentTimeMillis())) // 从消息中提取测量值，并创建SimpleMeasurementValue对象
            ;
    }

    // 配置元数据，用于描述配置参数的元信息。
    static ConfigMetadata configMetadata = new DefaultConfigMetadata()
        .add("deviceId", "设备", "指定设备", new StringType().expand("selector", "device-selector"));

    /**
     * 实时设备事件
     */
    private class RealTimeDevicePropertyDimension implements MeasurementDimension {

        /**
         * 获取维度定义。
         * @return 返回实时维度定义。
         */
        @Override
        public DimensionDefinition getDefinition() {
            return CommonDimensionDefinition.realTime;
        }

        /**
         * 获取值的类型。
         * @return 返回对象类型，包含事件和数据属性。
         */
        @Override
        public DataType getValueType() {
            // 定义“事件”属性元数据
            SimplePropertyMetadata property = new SimplePropertyMetadata();
            property.setId("event");
            property.setName("事件");
            property.setValueType(new StringType());

            // 定义“数据”属性元数据
            SimplePropertyMetadata value = new SimplePropertyMetadata();
            value.setId("data");
            value.setName("数据");
            value.setValueType(new StringType());

            // 返回包含事件和数据属性的对象类型
            return new ObjectType()
                .addPropertyMetadata(property)
                .addPropertyMetadata(value);
        }

        /**
         * 获取配置元数据。
         * @return 返回配置元数据。
         */
        @Override
        public ConfigMetadata getParams() {
            return configMetadata;
        }

        /**
         * 检查是否为实时测量。
         * @return 始终返回true，表示此维度是实时的。
         */
        @Override
        public boolean isRealTime() {
            return true;
        }

        /**
         * 根据参数获取测量值。
         * @param parameter 测量参数，包含设备ID和历史数据长度。
         * @return 返回一个包含历史和实时数据的测量值流。
         */
        @Override
        public Flux<MeasurementValue> getValue(MeasurementParameter parameter) {
            // 从参数中获取设备ID
            return Mono
                .justOrEmpty(parameter.getString("deviceId"))
                .flatMapMany(deviceId -> {
                    // 从参数中获取历史数据长度，默认为0
                    int history = parameter.getInt("history").orElse(0);
                    // 合并历史数据流和实时数据流
                    // 合并历史数据和实时数据
                    return Flux.concat(
                        // 加载指定设备的历史数据
                        //查询历史数据
                        fromHistory(deviceId, history)
                        // 订阅并返回指定设备的实时事件流
                        ,
                        //从消息网关订阅实时事件消息
                        fromRealTime(deviceId)
                    );
                });
        }
    }

}
