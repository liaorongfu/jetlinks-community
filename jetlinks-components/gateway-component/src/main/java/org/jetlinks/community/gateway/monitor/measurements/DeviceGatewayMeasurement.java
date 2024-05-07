package org.jetlinks.community.gateway.monitor.measurements;

import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.jetlinks.community.Interval;
import org.jetlinks.community.dashboard.*;
import org.jetlinks.community.dashboard.supports.StaticMeasurement;
import org.jetlinks.community.gateway.monitor.GatewayTimeSeriesMetric;
import org.jetlinks.community.timeseries.TimeSeriesManager;
import org.jetlinks.community.timeseries.query.Aggregation;
import org.jetlinks.community.timeseries.query.AggregationQueryParam;
import org.jetlinks.core.metadata.ConfigMetadata;
import org.jetlinks.core.metadata.DataType;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.DateTimeType;
import org.jetlinks.core.metadata.types.EnumType;
import org.jetlinks.core.metadata.types.IntType;
import org.jetlinks.core.metadata.types.StringType;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * DeviceGatewayMeasurement 类扩展了 StaticMeasurement，用于处理设备网关的测量数据。
 * 它支持历史数据和聚合数据的查询。
 */
class DeviceGatewayMeasurement extends StaticMeasurement {

    // 时间序列管理器，用于管理和查询时间序列数据
    private TimeSeriesManager timeSeriesManager;

    // 测量类型
    private String type;

    // 默认聚合方式
    private Aggregation defaultAgg;

    // 属性名，指定测量的属性
    private String property;

    /**
     * 构造函数，初始化设备网关测量实例。
     *
     * @param definition 测量定义
     * @param property 属性名
     * @param defaultAgg 默认聚合方式
     * @param timeSeriesManager 时间序列管理器
     */
    public DeviceGatewayMeasurement(MeasurementDefinition definition,
                                    String property,
                                    Aggregation defaultAgg,
                                    TimeSeriesManager timeSeriesManager) {
        super(definition);
        this.timeSeriesManager = timeSeriesManager;
        this.defaultAgg = defaultAgg;
        this.type = definition.getId();
        this.property = property;
        addDimension(new AggDeviceStateDimension());
        addDimension(new HistoryDimension());
    }

    // 历史数据配置元数据，定义了查询历史数据时可用的参数
    static ConfigMetadata historyConfigMetadata = new DefaultConfigMetadata()
        .add("gatewayId", "网关", "", new StringType())
        .add("time", "周期", "例如: 1h,10m,30s", new StringType())
        .add("format", "时间格式", "如: MM-dd:HH", new StringType())
        .add("limit", "最大数据量", "", new IntType())
        .add("from", "时间从", "", new DateTimeType().format("yyyy-MM-dd HH:mm:ss"))
        .add("to", "时间至", "", new DateTimeType().format("yyyy-MM-dd HH:mm:ss"));

    /**
     * HistoryDimension 类实现了 MeasurementDimension 接口，
     * 用于处理和获取设备网关的历史数据。
     */
    class HistoryDimension implements MeasurementDimension {

        /**
         * 获取维度定义。
         *
         * @return 维度定义
         */
        @Override
        public DimensionDefinition getDefinition() {
            return CommonDimensionDefinition.history;
        }

        /**
         * 获取数据类型。
         *
         * @return 数据类型
         */
        @Override
        public DataType getValueType() {
            return new IntType();
        }

        /**
         * 获取配置元数据。
         *
         * @return 配置元数据
         */
        @Override
        public ConfigMetadata getParams() {
            return historyConfigMetadata;
        }

        /**
         * 判断是否为实时数据。
         *
         * @return false，表示不是实时数据
         */
        @Override
        public boolean isRealTime() {
            return false;
        }

        /**
         * 根据参数获取历史数据值。
         *
         * @param parameter 查询参数
         * @return 数据流
         */
        @Override
        public Flux<SimpleMeasurementValue> getValue(MeasurementParameter parameter) {
            // 构建查询条件并执行查询，返回指定条件的历史数据
            return QueryParamEntity.newQuery()
                .where("target", type)
                .is("name", parameter.getString("gatewayId").orElse(null))
                .doPaging(0, parameter.getInt("limit").orElse(1))
                .between("timestamp",
                    parameter.getDate("from").orElseGet(() -> Date.from(LocalDateTime.now().plusDays(-1).atZone(ZoneId.systemDefault()).toInstant())),
                    parameter.getDate("to").orElseGet(Date::new)
                )
                .execute(timeSeriesManager.getService(GatewayTimeSeriesMetric.deviceGatewayMetric())::query)
                .map(data -> SimpleMeasurementValue.of(
                    data.getInt(property).orElse(0),
                    data.getTimestamp()))
                .sort(MeasurementValue.sort());
        }
    }

    // 聚合数据配置元数据，定义了查询聚合数据时可用的参数
    static ConfigMetadata aggConfigMetadata = new DefaultConfigMetadata()
        .add("gatewayId", "网关", "", new StringType())
        .add("time", "周期", "例如: 1h,10m,30s", new StringType())
        .add("format", "时间格式", "如: MM-dd:HH", new StringType())
        .add("agg", "聚合方式", "", new EnumType()
            .addElement(EnumType.Element.of("SUM", "总和"))
            .addElement(EnumType.Element.of("MAX", "最大值"))
            .addElement(EnumType.Element.of("MIN", "最小值"))
            .addElement(EnumType.Element.of("AVG", "平局值"))
        )
        .add("limit", "最大数据量", "", new IntType())
        .add("from", "时间从", "", new DateTimeType().format("yyyy-MM-dd HH:mm:ss"))
        .add("to", "时间至", "", new DateTimeType().format("yyyy-MM-dd HH:mm:ss"));

    /**
     * AggDeviceStateDimension 类实现了 MeasurementDimension 接口，
     * 用于处理和获取设备网关的聚合数据。
     */
    class AggDeviceStateDimension implements MeasurementDimension {

        /**
         * 获取维度定义。
         *
         * @return 维度定义
         */
        @Override
        public DimensionDefinition getDefinition() {
            return CommonDimensionDefinition.agg;
        }

        /**
         * 获取数据类型。
         *
         * @return 数据类型
         */
        @Override
        public DataType getValueType() {
            return new IntType();
        }

        /**
         * 获取配置元数据。
         *
         * @return 配置元数据
         */
        @Override
        public ConfigMetadata getParams() {
            return aggConfigMetadata;
        }

        /**
         * 判断是否为实时数据。
         *
         * @return false，表示不是实时数据
         */
        @Override
        public boolean isRealTime() {
            return false;
        }

        /**
         * 根据参数获取聚合数据值。
         *
         * @param parameter 查询参数
         * @return 数据流
         */
        @Override
        public Flux<SimpleMeasurementValue> getValue(MeasurementParameter parameter) {
            // 构建聚合查询条件并执行查询，返回指定条件的聚合数据
            return AggregationQueryParam.of()
                .agg(property, parameter.get("agg", Aggregation.class).orElse(defaultAgg))
                .groupBy(parameter.getInterval("time").orElse(Interval.ofHours(1)),
                    "time",
                    parameter.getString("format").orElse("MM-dd:HH"))
                .filter(query -> query
                    .where("target", type)
                    .is("name", parameter.getString("gatewayId").orElse(null)))
                .limit(parameter.getInt("limit").orElse(1))
                .from(parameter.getDate("from").orElseGet(() -> Date.from(LocalDateTime.now().plusDays(-1).atZone(ZoneId.systemDefault()).toInstant())))
                .to(parameter.getDate("to").orElse(new Date()))
                .execute(timeSeriesManager.getService(GatewayTimeSeriesMetric.deviceGatewayMetric())::aggregation)
                .index((index, data) -> SimpleMeasurementValue.of(
                    data.getInt(property).orElse(0),
                    data.getString("time").orElse(""),
                    index))
                .sort();
        }
    }

}

