package org.jetlinks.community.device.measurements.message;

import lombok.Generated;
import org.jetlinks.community.Interval;
import org.jetlinks.community.dashboard.*;
import org.jetlinks.community.dashboard.supports.StaticMeasurement;
import org.jetlinks.community.device.timeseries.DeviceTimeSeriesMetric;
import org.jetlinks.community.timeseries.TimeSeriesManager;
import org.jetlinks.community.timeseries.TimeSeriesMetric;
import org.jetlinks.community.timeseries.query.AggregationQueryParam;
import org.jetlinks.core.device.DeviceProductOperator;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import org.jetlinks.core.event.TopicPayload;
import org.jetlinks.core.metadata.ConfigMetadata;
import org.jetlinks.core.metadata.DataType;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.DateTimeType;
import org.jetlinks.core.metadata.types.IntType;
import org.jetlinks.core.metadata.types.StringType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
/**
 * 事件总线，用于订阅实时设备消息。
 */
class DeviceMessageMeasurement extends StaticMeasurement {
    /**
     * 事件总线，用于订阅实时设备消息。
     */
    private final EventBus eventBus;
    /**
     * 时间序列管理器，用于查询历史设备消息。
     */
    private final TimeSeriesManager timeSeriesManager;
    /**
     * 设备消息测量的定义，包括测量名称和描述。
     */
    static MeasurementDefinition definition = MeasurementDefinition.of("quantity", "设备消息量");
    /**
     * 构造函数，初始化设备消息测量实例。
     *
     * @param eventBus        事件总线
     * @param timeSeriesManager  时间序列管理器
     */
    public DeviceMessageMeasurement(EventBus eventBus,
                                    TimeSeriesManager timeSeriesManager) {
        super(definition);
        this.eventBus = eventBus;
        this.timeSeriesManager = timeSeriesManager;
        addDimension(new RealTimeMessageDimension());
        addDimension(new AggMessageDimension());
    }
    /**
     * 实时消息维度的配置元数据，包括数据统计周期。
     */
    static ConfigMetadata realTimeConfigMetadata = new DefaultConfigMetadata()
        .add("interval", "数据统计周期", "例如: 1s,10s", new StringType());
    /**
     * 实时消息维度类，实现MeasurementDimension接口，用于统计实时设备消息量。
     */
    class RealTimeMessageDimension implements MeasurementDimension {
        /**
         * 获取维度定义。
         */
        @Override
        @Generated
        public DimensionDefinition getDefinition() {
            return CommonDimensionDefinition.realTime;
        }
        /**
         * 获取值的数据类型。
         */
        @Override
        @Generated
        public DataType getValueType() {
            return IntType.GLOBAL;
        }
        /**
         * 获取维度的配置元数据。
         */
        @Override
        @Generated
        public ConfigMetadata getParams() {
            return realTimeConfigMetadata;
        }
        /**
         * 判断是否为实时维度。
         */
        @Override
        @Generated
        public boolean isRealTime() {
            return true;
        }
        /**
         * 订阅事件总线，统计指定周期内的设备消息量。
         */
        @Override
        public Flux<MeasurementValue> getValue(MeasurementParameter parameter) {


            //通过订阅消息来统计实时数据量
            return eventBus
                .subscribe(Subscription.of("real-time-device-message", "/device/**", Subscription.Feature.local, Subscription.Feature.broker))
                .doOnNext(TopicPayload::release)
                .window(parameter.getDuration("interval").orElse(Duration.ofSeconds(1)))
                .flatMap(Flux::count)
                .map(total -> SimpleMeasurementValue.of(total, System.currentTimeMillis()));
        }
    }
    /**
     * 历史消息维度的配置元数据，包括设备型号、统计周期、时间格式、消息类型、最大数据量、时间范围等。
     */
    static ConfigMetadata historyConfigMetadata = new DefaultConfigMetadata()
        .add("productId", "设备型号", "", new StringType())
        .add("time", "周期", "例如: 1h,10m,30s", new StringType())
        .add("format", "时间格式", "如: MM-dd:HH", new StringType())
        .add("msgType", "消息类型", "", new StringType())
        .add("limit", "最大数据量", "", new IntType())
        .add("from", "时间从", "", new DateTimeType())
        .add("to", "时间至", "", new DateTimeType());
    /**
     * 历史消息维度类，实现MeasurementDimension接口，用于查询历史设备消息量。
     */
    class AggMessageDimension implements MeasurementDimension {

        /**
         * 获取维度定义。
         */
        @Override
        @Generated
        public DimensionDefinition getDefinition() {
            return CommonDimensionDefinition.agg;
        }
        /**
         * 获取值的数据类型。
         */
        @Override
        @Generated
        public DataType getValueType() {
            return IntType.GLOBAL;
        }

        /**
         * 获取配置元数据。
         *
         * 此方法用于返回配置历史的元数据。元数据包含关于配置的结构和类型信息，
         * 但不包含实际的配置值。这样设计是为了允许在不暴露具体配置值的情况下，
         * 对配置结构进行查询和操作。
         *
         * @return ConfigMetadata 对象，代表配置的元数据。
         */
        @Override
        @Generated
        public ConfigMetadata getParams() {
            return historyConfigMetadata;
        }


        /**
         * 判断当前对象是否支持实时特性。
         *
         * 此方法用于回答对象是否能够在需要时提供实时数据或操作。返回false表明
         * 该对象不保证提供实时特性，可能因为数据更新频率、处理延迟或其他因素。
         *
         * @return boolean - 如果对象支持实时特性则返回true，否则返回false。
         */
        @Override
        @Generated
        public boolean isRealTime() {
            return false;
        }


        /**
         * 根据测量参数创建聚合查询参数。
         *
         * @param parameter 包含查询所需信息的测量参数对象。
         * @return 构建完成的聚合查询参数对象。
         */
        public AggregationQueryParam createQueryParam(MeasurementParameter parameter) {
            // 创建一个新的聚合查询参数对象
            return AggregationQueryParam
                .of()
                // 定义要计算的总和字段
                .sum("count")
                // 根据时间间隔和格式对结果进行分组
                .groupBy(parameter.getInterval("interval", parameter.getInterval("time", null)),
                         parameter.getString("format").orElse("MM月dd日 HH时"))
                // 添加过滤条件，筛选出名称为"message-count"且产品ID匹配的记录
                .filter(query -> query
                    .where("name", "message-count")
                    .is("productId", parameter.getString("productId").orElse(null))
                )
                // 设置返回结果的数量限制
                .limit(parameter.getInt("limit").orElse(1))
                // 设置查询的起始时间，如果未指定，则默认为当前时间的前一天
                .from(parameter
                          .getDate("from")
                          .orElseGet(() -> Date
                              .from(LocalDateTime
                                        .now()
                                        .plusDays(-1)
                                        .atZone(ZoneId.systemDefault())
                                        .toInstant())))
                // 设置查询的结束时间
                .to(parameter.getDate("to").orElse(new Date()));
        }


        /**
         * 根据测量参数获取测量值流。
         *
         * 此方法通过创建查询参数并执行时间序列数据的聚合查询，来获取一系列简单的测量值。
         * 它使用Reactive Streams的Flux API来延迟执行查询，并按需提供查询结果。
         *
         * @param parameter 测量参数，用于定义查询的具体条件。
         * @return 返回一个Flux流，包含根据查询结果转换得到的简单测量值。
         */
        @Override
        public Flux<SimpleMeasurementValue> getValue(MeasurementParameter parameter) {
            // 根据测量参数创建聚合查询参数对象
            AggregationQueryParam param = createQueryParam(parameter);

            // 延迟创建并返回一个Flux流，该流通过执行聚合查询来提供SimpleMeasurementValue对象
            return Flux.defer(() -> param
                // 执行聚合查询，并使用提供的函数将查询结果转换为SimpleMeasurementValue对象
                .execute(timeSeriesManager.getService(DeviceTimeSeriesMetric.deviceMetrics())::aggregation)
                // 对查询结果的每一项进行索引，将其转换为SimpleMeasurementValue对象
                .index((index, data) -> SimpleMeasurementValue.of(
                    // 从数据中提取计数值，如果不存在则默认为0
                    data.getLong("count",0),
                    // 从数据中提取时间字符串，如果不存在则为空字符串
                    data.getString("time").orElse(""),
                    // 提取数据的索引作为测量值的一部分
                    index)))
                // 限制返回的测量值数量，根据查询参数中的限制条件
                .take(param.getLimit());
        }

    }

}
