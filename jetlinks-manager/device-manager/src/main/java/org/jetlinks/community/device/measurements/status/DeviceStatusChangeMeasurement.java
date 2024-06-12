package org.jetlinks.community.device.measurements.status;

import org.jetlinks.community.Interval;
import org.jetlinks.community.dashboard.*;
import org.jetlinks.community.dashboard.supports.StaticMeasurement;
import org.jetlinks.community.device.timeseries.DeviceTimeSeriesMetric;
import org.jetlinks.community.timeseries.TimeSeriesManager;
import org.jetlinks.community.timeseries.query.AggregationQueryParam;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.MessageType;
import org.jetlinks.core.metadata.ConfigMetadata;
import org.jetlinks.core.metadata.DataType;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.DateTimeType;
import org.jetlinks.core.metadata.types.EnumType;
import org.jetlinks.core.metadata.types.IntType;
import org.jetlinks.core.metadata.types.StringType;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
/**
 * 设备状态变更测量类，用于处理设备状态变更事件并生成相应的时间序列数据。
 * 该类继承自MeasurementBase，实现了对设备状态变更的测量定义和配置。
 */
class DeviceStatusChangeMeasurement extends StaticMeasurement {
    // 事件总线，用于订阅和发布事件。
    private final EventBus eventBus;
    // 时间序列管理器，用于管理时间序列数据的存储和查询。
    private final TimeSeriesManager timeSeriesManager;
    // 测量定义，指定测量的名称和描述。
    static MeasurementDefinition definition = MeasurementDefinition.of("change", "设备状态变更");
    // 配置元数据，用于配置测量的参数。
    static ConfigMetadata configMetadata = new DefaultConfigMetadata()
        .add("deviceId", "设备", "指定设备", new StringType().expand("selector", "device-selector"));
    // 数据类型定义，用于定义测量数据的类型。
    static DataType type = new EnumType()
        .addElement(EnumType.Element.of(MessageType.OFFLINE.name().toLowerCase(), "离线"))
        .addElement(EnumType.Element.of(MessageType.ONLINE.name().toLowerCase(), "在线"));
    /**
     * 构造函数，初始化设备状态变更测量类。
     *
     * @param timeSeriesManager 时间序列管理器，用于处理时间序列数据。
     * @param eventBus 事件总线，用于订阅和发布事件。
     */
    public DeviceStatusChangeMeasurement(TimeSeriesManager timeSeriesManager, EventBus eventBus) {
        super(definition);
        this.eventBus = eventBus;
        this.timeSeriesManager = timeSeriesManager;
        addDimension(new RealTimeDeviceStateDimension());
        addDimension(new CountDeviceStateDimension());
    }
    // 历史状态查询的配置元数据，用于配置历史状态查询的参数。
    static ConfigMetadata historyConfigMetadata = new DefaultConfigMetadata()
        .add("time", "周期", "例如: 1h,10m,30s", new StringType())
        .add("format", "时间格式", "如: MM-dd:HH", new StringType())
        .add("type", "类型", "上线or离线", new EnumType()
            .addElement(EnumType.Element.of("online", "上线"))
            .addElement(EnumType.Element.of("offline", "离线")))
        .add("limit", "最大数据量", "", new IntType())
        .add("from", "时间从", "", new DateTimeType())
        .add("to", "时间至", "", new DateTimeType());
    // 历史状态查询的数据类型定义，指定查询结果的数据类型。
    static DataType historyValueType = new IntType();

    /**
     * 设备状态统计
     */
    class CountDeviceStateDimension implements MeasurementDimension {

        /**
         * 获取维度定义。
         *
         * 本方法重写了超类方法，旨在返回特定的维度定义对象。维度定义在系统中用于规范和描述
         * 数据库表中的一个维度，例如时间维度、地域维度等。通过返回预定义的维度定义对象，
         * 可以确保系统中对特定维度的处理逻辑和属性保持一致。
         *
         * @return 返回维度定义对象。此处返回的是CommonDimensionDefinition类中定义的
         *         agg维度定义，该定义可能包含了维度的属性、层次和常用聚合函数等信息。
         */
        @Override
        public DimensionDefinition getDefinition() {
            return CommonDimensionDefinition.agg;
        }


        /**
         * 获取值的类型。
         *
         * @return 返回数据类型的枚举值，指示此历史记录对象所持有的数据类型。
         */
        @Override
        public DataType getValueType() {
            return historyValueType;
        }


        /**
         * 获取参数配置元数据。
         *
         * @return ConfigMetadata 对象，代表参数配置的元数据。
         */
        @Override
        public ConfigMetadata getParams() {
            return historyConfigMetadata;
        }

        /**
         * 判断当前环境是否为实时环境。
         *
         * @return 如果是实时环境，返回true；否则返回false。
         *
         * 注意：此处返回false，表示当前环境不支持实时性要求。
         */
        @Override
        public boolean isRealTime() {
            return false;
        }


        /**
         * 根据测量参数获取测量值的Flux流。
         *
         * @param parameter 测量参数，包含查询所需的各种条件。
         * @return 返回一个Flux流，其中包含根据参数查询到的简单测量值。
         */
        @Override
        public Flux<SimpleMeasurementValue> getValue(MeasurementParameter parameter) {
            // 根据参数指定的格式，或默认格式创建日期格式化器
            String format = parameter.getString("format").orElse("yyyy年MM月dd日");
            DateTimeFormatter formatter = DateTimeFormat.forPattern(format);

            // 构建查询条件，包括聚合函数、分组条件、过滤条件、限制条件等
            return AggregationQueryParam.of()
                .sum("count")
                .groupBy(parameter.getInterval("time", Interval.ofDays(1)), format)
                .filter(query ->
                    query.where("name", parameter.getString("type").orElse("online"))
                        .is("productId", parameter.getString("productId").orElse(null))
                )
                .limit(parameter.getInt("limit").orElse(1))
                .from(parameter.getDate("from").orElse(Date.from(LocalDateTime.now().plusDays(-1).atZone(ZoneId.systemDefault()).toInstant())))
                .to(parameter.getDate("to").orElse(new Date()))
                // 执行查询，并使用时间序列管理器的服务进行数据聚合
                .execute(timeSeriesManager.getService(DeviceTimeSeriesMetric.deviceMetrics())::aggregation)
                // 对查询结果进行映射，转换为SimpleMeasurementValue对象
                .map(data -> {
                    // 解析时间字符串为毫秒级时间戳
                    long ts = data.getString("time")
                        .map(time -> DateTime.parse(time, formatter).getMillis())
                        .orElse(System.currentTimeMillis());
                    // 创建并返回简单测量值对象
                    return SimpleMeasurementValue.of(
                        data.get("count").orElse(0),
                        data.getString("time", ""),
                        ts);
                })
                // 对结果进行排序
                .sort();
        }

    }

    /**
     * 实时设备变更状态
     * 实时设备状态维度类，实现了MeasurementDimension接口。
     * 用于定义和处理与设备实时状态相关的测量维度。
     */
    class RealTimeDeviceStateDimension implements MeasurementDimension {

        /**
         * 获取维度定义。
         * 返回通用维度定义中的实时维度。
         *
         * @return DimensionDefinition 维度定义
         */
        @Override
        public DimensionDefinition getDefinition() {
            return CommonDimensionDefinition.realTime;
        }

        /**
         * 获取值的数据类型。
         * 返回此维度测量值的数据类型。
         *
         * @return DataType 值的数据类型
         */
        @Override
        public DataType getValueType() {
            return type;
        }

        /**
         * 获取配置元数据。
         * 返回此维度的配置元数据。
         *
         * @return ConfigMetadata 配置元数据
         */
        @Override
        public ConfigMetadata getParams() {
            return configMetadata;
        }

        /**
         * 判断是否为实时测量。
         * 对于此维度，始终返回true，表示是实时测量。
         *
         * @return boolean 是否为实时测量
         */
        @Override
        public boolean isRealTime() {
            return true;
        }

        /**
         * 根据测量参数获取实时测量值的流。
         * 本方法通过订阅设备的在线和离线状态更新来实现。它首先从测量参数中提取设备ID，
         * 然后使用这个设备ID来订阅与该设备状态变化相关的事件。
         * 订阅的事件包括设备上线和设备下线，这些事件被转换为测量值，然后作为Flux流返回。
         *
         * @param parameter 包含设备ID等信息的测量参数对象。
         * @return 返回一个Flux流，该流不断发布设备的实时状态测量值。
         */
        @Override
        public Flux<MeasurementValue> getValue(MeasurementParameter parameter) {
            // 从参数中提取设备ID，如果设备ID不存在，则返回空Mono。
            // 从参数中提取设备ID
            return Mono.justOrEmpty(parameter.getString("deviceId"))
                .flatMapMany(deviceId ->//从消息网关订阅消息
                    // 订阅设备的在线和离线状态事件。
                    eventBus.subscribe(Subscription.of(
                        "RealTimeDeviceStateDimension"
                        , new String[]{
                            "/device/*/" + deviceId + "/online",
                            "/device/*/" + deviceId + "/offline"
                        },
                        Subscription.Feature.local,
                        Subscription.Feature.broker
                    ), DeviceMessage.class)
                        // 将设备消息转换为测量值。
                        .map(msg -> SimpleMeasurementValue.of(createStateValue(msg), msg.getTimestamp())));
        }


        /**
         * 根据设备消息创建状态值。
         * <p>
         * 本函数旨在将设备消息转换为一个包含特定状态信息的Map对象，以便于后续处理或存储。
         * 通过提取设备消息中的消息类型和设备ID，将其封装到Map中，以键值对的形式表示。
         * 其中，消息类型经过转换，统一为小写形式，以确保数据的一致性和可比较性。
         *
         * @param message 设备消息对象，包含消息类型和设备ID等信息。
         * @return 返回一个Map对象，其中包含"类型"和"设备ID"两个键值对。
         */
        Map<String, Object> createStateValue(DeviceMessage message) {
            // 初始化一个HashMap用于存储状态值
            Map<String, Object> val = new HashMap<>();
            // 将消息类型转换为小写，并存储到Map中
            val.put("type", message.getMessageType().name().toLowerCase());
            // 将设备ID存储到Map中
            val.put("deviceId", message.getDeviceId());
            // 返回包含状态信息的Map对象
            return val;
        }
    }
}
