package org.jetlinks.community.device.measurements;

import org.hswebframework.utils.time.DateFormatter;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.jetlinks.community.Interval;
import org.jetlinks.community.dashboard.*;
import org.jetlinks.community.dashboard.supports.StaticMeasurement;
import org.jetlinks.community.device.service.data.DeviceDataService;
import org.jetlinks.community.gateway.DeviceMessageUtils;
import org.jetlinks.community.timeseries.query.Aggregation;
import org.jetlinks.community.timeseries.query.AggregationData;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.metadata.*;
import org.jetlinks.core.metadata.types.IntType;
import org.jetlinks.core.metadata.types.NumberType;
import org.jetlinks.core.metadata.types.ObjectType;
import org.jetlinks.core.metadata.types.StringType;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 设备属性测量类，继承自StaticMeasurement，用于处理设备属性的静态测量。
 * 该类提供了从历史和实时数据中获取测量值的能力，并支持对数值类型属性进行聚合计算。
 */
class DevicePropertyMeasurement extends StaticMeasurement {

    // 设备属性元数据，包含属性的定义和配置信息。
    private final PropertyMetadata metadata;
    // 事件总线，用于订阅和发布设备属性的实时变化事件。
    private final EventBus eventBus;
    // 设备数据服务，用于访问和处理设备属性数据。
    private final DeviceDataService deviceDataService;
    // 产品ID，用于标识设备所属的产品。
    private final String productId;

    /**
     * 构造函数，初始化设备属性测量实例。
     *
     * @param productId 产品ID
     * @param eventBus 事件总线
     * @param metadata 属性元数据
     * @param deviceDataService 设备数据服务
     */
    public DevicePropertyMeasurement(String productId,
                                     EventBus eventBus,
                                     PropertyMetadata metadata,
                                     DeviceDataService deviceDataService) {
        super(MetadataMeasurementDefinition.of(metadata));
        this.productId = productId;
        this.eventBus = eventBus;
        this.metadata = metadata;
        this.deviceDataService = deviceDataService;
        // 根据属性类型添加相应的维度，用于支持不同的数据处理和展示方式。
        addDimension(new RealTimeDevicePropertyDimension());
        addDimension(new HistoryDevicePropertyDimension());
        if (metadata.getValueType() instanceof NumberType) {
            addDimension(new AggDevicePropertyDimension());
        }
    }

    /**
     * 根据属性值创建测量值对象。
     *
     * @param value 属性值
     * @return 包含属性值和格式化值的Map
     */
    Map<String, Object> createValue(Object value) {
        Map<String, Object> values = new HashMap<>();
        DataType type = metadata.getValueType();
        value = type instanceof Converter ? ((Converter<?>) type).convert(value) : value;
        values.put("value", value);
        values.put("formatValue", type.format(value));
        return values;
    }

    /**
     * 根据设备ID和历史记录数量，查询设备的历史测量值。
     *
     * @param deviceId 设备的唯一标识符。
     * @param history 指定查询的历史记录数量。如果历史记录数量小于等于0，则不查询。
     * @return 返回一个Flux流，包含设备的历史测量值。如果历史记录数量小于等于0，则返回一个空的Flux流。
     */
    Flux<SimpleMeasurementValue> fromHistory(String deviceId, int history) {
        // 根据history的值判断是否查询历史记录
        return history <= 0
            ? Flux.empty() // 如果history小于等于0，返回空的Flux流
            : QueryParamEntity
                .newQuery()
                // 设置分页参数，查询第0页到第history页
                .doPaging(0, history)
                // 执行查询，传入设备ID和查询参数，获取设备的历史测量数据
                .execute(q -> deviceDataService.queryProperty(deviceId, q, metadata.getId()))
                // 将查询结果转换为SimpleMeasurementValue对象，并包含时间戳
                .map(data -> SimpleMeasurementValue.of(data, data.getTimestamp()))
                // 对查询结果进行排序
                .sort(MeasurementValue.sort());
    }

    /**
     * 根据指定设备ID，从实时数据流中订阅设备属性测量值。
     *
     * @param deviceId 设备ID，用于指定要订阅的设备。
     * @return 返回一个Flux流，包含所订阅设备的实时测量值。
     */
    Flux<MeasurementValue> fromRealTime(String deviceId) {
        // 创建设备属性实时测量事件的订阅对象。
        org.jetlinks.core.event.Subscription subscription = org.jetlinks.core.event.Subscription.of(
            "realtime-device-property-measurement",
            new String[]{
                "/device/" + productId + "/" + deviceId + "/message/property/report",
                "/device/" + productId + "/" + deviceId + "/message/property/*/reply"
            },
            org.jetlinks.core.event.Subscription.Feature.local, org.jetlinks.core.event.Subscription.Feature.broker
        );

        // 通过事件总线订阅设备属性事件，并处理事件消息。
        return eventBus
            .subscribe(subscription, DeviceMessage.class)
            .flatMap(msg -> Mono.justOrEmpty(DeviceMessageUtils.tryGetProperties(msg)))
            .filter(msg -> msg.containsKey(metadata.getId())) // 过滤出包含指定元数据ID的消息。
            .map(msg -> SimpleMeasurementValue.of(createValue(msg.get(metadata.getId())), System.currentTimeMillis()));
    }

    static ConfigMetadata configMetadata = new DefaultConfigMetadata()
        .add("deviceId", "设备", "指定设备", new StringType().expand("selector", "device-selector"))
        .add("history", "历史数据量", "查询出历史数据后开始推送实时数据", new IntType().min(0).expand("defaultValue", 10))
        .add("from", "时间从", "", StringType.GLOBAL)
        .add("to", "时间至", "", StringType.GLOBAL);
    ;

    static ConfigMetadata aggConfigMetadata = new DefaultConfigMetadata()
        .add("deviceId", "设备ID", "", StringType.GLOBAL)
        .add("time", "周期", "例如: 1h,10m,30s", StringType.GLOBAL)
        .add("agg", "聚合类型", "count,sum,avg,max,min", StringType.GLOBAL)
        .add("format", "时间格式", "如: MM-dd:HH", StringType.GLOBAL)
        .add("limit", "最大数据量", "", StringType.GLOBAL)
        .add("from", "时间从", "", StringType.GLOBAL)
        .add("to", "时间至", "", StringType.GLOBAL);

    /**
     * 聚合数据
     */
    private class AggDevicePropertyDimension implements MeasurementDimension {

        @Override
        public DimensionDefinition getDefinition() {
            return CommonDimensionDefinition.agg;
        }

        /**
         * 获取值的类型。
         *
         * 本方法旨在描述一个复杂对象的类型，该对象包含一个“值”属性，该属性本身是一个对象，
         * 具有“属性”、“值”和“格式化值”三个属性。此外，还包括一个“时间字符串”属性。
         * 这种设计用于表示具有时间和相关属性的复杂数据类型。
         *
         * @return ObjectType 对象，代表此数据类型的结构。其中包括“值”和“时间字符串”两个属性。
         * “值”属性本身是一个对象，包含关于属性、实际值和格式化值的信息。
         */
        @Override
        public DataType getValueType() {
            // 创建一个新的ObjectType实例，用于描述本数据类型的结构。
            return new ObjectType()
                // 添加“值”属性，该属性是一个对象，具有多个子属性。
                .addProperty("value", "数据", new ObjectType()
                    // “值”对象中的“属性”属性，表示属性的名称。
                    .addProperty("property", StringType.GLOBAL)
                    // “值”对象中的“值”属性，表示属性的值，其类型由metadata.getValueType()决定。
                    .addProperty("value", metadata.getValueType())
                    // “值”对象中的“格式化值”属性，用于显示值的格式化版本。
                    .addProperty("formatValue", StringType.GLOBAL))
                // 添加“时间字符串”属性，用于表示时间的字符串形式。
                .addProperty("timeString", "时间", StringType.GLOBAL);
        }


        @Override
        public ConfigMetadata getParams() {
            return aggConfigMetadata;
        }

        @Override
        public boolean isRealTime() {
            return false;
        }

        /**
         * 根据测量参数获取设备的测量值。
         *
         * @param parameter 包含查询所需参数的MeasurementParameter对象。
         * @return 返回一个Flux流，包含SimpleMeasurementValue对象，这些对象根据查询参数从设备数据中聚合得出。
         */
        @Override
        public Flux<SimpleMeasurementValue> getValue(MeasurementParameter parameter) {
            // 从参数中获取设备ID，如果不存在则使用null
            String deviceId = parameter.getString("deviceId", null);

            // 初始化聚合请求对象
            DeviceDataService.AggregationRequest request = new DeviceDataService.AggregationRequest();
            // 根据参数中的agg字段确定聚合方式，默认为平均值（AVG）
            DeviceDataService.DevicePropertyAggregation aggregation = new DeviceDataService.DevicePropertyAggregation(
                metadata.getId(), metadata.getId(), parameter.getString("agg").map(String::toUpperCase).map(Aggregation::valueOf).orElse(Aggregation.AVG)
            );

            // 从参数中获取格式字符串，默认为"HH:mm:ss"
            String format = parameter.getString("format", "HH:mm:ss");
            // 根据格式字符串创建日期格式化器
            DateTimeFormatter formatter = DateTimeFormat.forPattern(format);

            // 根据参数设置聚合请求的限制、时间间隔、格式和时间范围
            request.setLimit(parameter.getInt("limit", 10));
            request.setInterval(parameter.getInterval("time", Interval.ofSeconds(10)));
            request.setFormat(format);
            request.setFrom(parameter.getDate("from", DateTime.now().plusDays(-1).toDate()));
            request.setTo(parameter.getDate("to", DateTime.now().plusDays(-1).toDate()));

            Flux<AggregationData> dataFlux;
            // 根据是否有设备ID，决定是针对单个设备还是整个产品进行聚合查询
            if (StringUtils.hasText(deviceId)) {
                dataFlux = deviceDataService
                    .aggregationPropertiesByDevice(deviceId, request, aggregation);
            } else {
                dataFlux = deviceDataService.aggregationPropertiesByProduct(productId, request, aggregation);
            }

            // 对查询结果进行处理，转换为SimpleMeasurementValue对象，并按时间排序
            return dataFlux
                .map(data -> {
                    // 从数据中解析时间戳，如果不存在则使用当前时间戳
                    long ts = data.getString("time")
                        .map(time -> DateTime.parse(time, formatter).getMillis())
                        .orElse(System.currentTimeMillis());
                    // 根据数据创建SimpleMeasurementValue对象
                    return SimpleMeasurementValue.of(createValue(
                        data.get(metadata.getId()).orElse(0)),
                        data.getString("time",""),
                        ts);
                })
                .sort();
        }

    }

    /**
     * 历史设备数据
     */
    private class HistoryDevicePropertyDimension implements MeasurementDimension {

        @Override
        public DimensionDefinition getDefinition() {
            return CommonDimensionDefinition.history;
        }

        @Override
        public DataType getValueType() {
            return new ObjectType()
                .addProperty("property", "属性", StringType.GLOBAL)
                .addProperty("value", "值", metadata.getValueType())
                .addProperty("formatValue", "格式化值", StringType.GLOBAL);
        }

        @Override
        public ConfigMetadata getParams() {
            return configMetadata;
        }

        @Override
        public boolean isRealTime() {
            return false;
        }

        /**
         * 根据测量参数获取测量值的流。
         *
         * @param parameter 测量参数，包含设备ID、历史数据查询范围及其他查询条件。
         * @return 返回一个Flux流，包含查询到的测量值。
         */
        @Override
        public Flux<MeasurementValue> getValue(MeasurementParameter parameter) {
            // 从参数中尝试获取设备ID，如果存在则继续查询，否则返回空流。
            return Mono.justOrEmpty(parameter.getString("deviceId"))
                .flatMapMany(deviceId -> {
                    // 从参数中获取查询的历史数据条数，默认为1。
                    int history = parameter.getInt("history").orElse(1);

                    // 构建查询条件，分页查询从指定日期范围内的数据。
                    return  QueryParamEntity.newQuery()
                        .doPaging(0, history)
                        .as(query -> query
                            .gte("timestamp", parameter.getDate("from").orElse(null))
                            .lte("timestamp", parameter.getDate("to").orElse(null)))
                        // 根据设备ID和查询条件执行查询，并转换查询结果为测量值对象。
                        .execute(q -> deviceDataService.queryProperty(deviceId, q, metadata.getId()))
                        .map(data -> SimpleMeasurementValue.of(
                            data,
                            DateFormatter.toString(new Date(data.getTimestamp()), parameter.getString("timeFormat", "HH:mm:ss")),
                            data.getTimestamp()))
                        // 对查询结果进行排序。
                        .sort(MeasurementValue.sort());
                });
        }

    }

    /**
     * 实时设备事件
     */
    private class RealTimeDevicePropertyDimension implements MeasurementDimension {

        @Override
        public DimensionDefinition getDefinition() {
            return CommonDimensionDefinition.realTime;
        }

        @Override
        public DataType getValueType() {
            return new ObjectType()
                .addProperty("property", "属性", StringType.GLOBAL)
                .addProperty("value", "值", metadata.getValueType())
                .addProperty("formatValue", "格式化值", StringType.GLOBAL);
        }

        @Override
        public ConfigMetadata getParams() {
            return configMetadata;
        }

        @Override
        public boolean isRealTime() {
            return true;
        }

        @Override
        public Flux<MeasurementValue> getValue(MeasurementParameter parameter) {
            return Mono.justOrEmpty(parameter.getString("deviceId"))
                .flatMapMany(deviceId -> {
                    int history = parameter.getInt("history").orElse(0);
                    return  //合并历史数据和实时数据
                        Flux.concat(
                            //查询历史数据
                            fromHistory(deviceId, history)
                            ,
                            //从消息网关订阅实时事件消息
                            fromRealTime(deviceId)
                        );
                });
        }
    }


}
