package org.jetlinks.community.device.measurements.status;

import org.jetlinks.community.Interval;
import org.jetlinks.community.dashboard.*;
import org.jetlinks.community.dashboard.supports.StaticMeasurement;
import org.jetlinks.community.device.entity.DeviceInstanceEntity;
import org.jetlinks.community.device.enums.DeviceState;
import org.jetlinks.community.device.service.LocalDeviceInstanceService;
import org.jetlinks.community.device.timeseries.DeviceTimeSeriesMetric;
import org.jetlinks.community.timeseries.TimeSeriesManager;
import org.jetlinks.community.timeseries.query.AggregationQueryParam;
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
/**
 * 设备状态记录测量类，继承自StaticMeasurement，用于记录和处理设备状态相关的测量数据。
 */
class DeviceStatusRecordMeasurement
    extends StaticMeasurement {
    /**
     * 设备实例服务，用于获取设备实例相关信息。
     */
    public LocalDeviceInstanceService instanceService;
    /**
     * 时间序列管理器，用于处理时间序列数据。
     */
    private TimeSeriesManager timeSeriesManager;
    /**
     * 测量定义，定义了测量的名称和描述。
     */
    static MeasurementDefinition definition = MeasurementDefinition.of("record", "设备状态记录");
    /**
     * 构造函数，初始化DeviceStatusRecordMeasurement实例。
     *
     * @param deviceInstanceService 设备实例服务。
     * @param timeSeriesManager 时间序列管理器。
     */
    public DeviceStatusRecordMeasurement(LocalDeviceInstanceService deviceInstanceService,
                                         TimeSeriesManager timeSeriesManager) {
        super(definition);
        this.timeSeriesManager = timeSeriesManager;
        this.instanceService = deviceInstanceService;
        addDimension(new CurrentNumberOfDeviceDimension());
        addDimension(new AggNumberOfOnlineDeviceDimension());
    }
    /**
     * 集合配置元数据，用于配置聚合维度的参数。
     */
    static ConfigMetadata aggConfigMetadata = new DefaultConfigMetadata()
        .add("productId", "设备型号", "", new StringType())
        .add("time", "周期", "例如: 1h,10m,30s", new StringType())
        .add("format", "时间格式", "如: MM-dd:HH", new StringType())
        .add("limit", "最大数据量", "", new IntType())
        .add("from", "时间从", "", new DateTimeType())
        .add("to", "时间至", "", new DateTimeType());


    //历史在线数量
    class AggNumberOfOnlineDeviceDimension implements MeasurementDimension {
        /**
         * 获取维度定义。
         *
         * @return 维度定义。
         */
        @Override
        public DimensionDefinition getDefinition() {
            return DimensionDefinition.of("aggOnline", "历史在线数");
        }
        /**
         * 获取数据类型。
         *
         * @return 数据类型。
         */
        @Override
        public DataType getValueType() {
            return new IntType();
        }
        /**
         * 获取配置元数据。
         *
         * @return 配置元数据。
         */
        @Override
        public ConfigMetadata getParams() {
            return aggConfigMetadata;
        }
        /**
         * 判断是否为实时维度。
         *
         * @return 是否为实时维度。
         */
        @Override
        public boolean isRealTime() {
            return false;
        }

        /**
         * 根据测量参数获取值的流。
         *
         * @param parameter 测量参数，包含查询所需的各种条件。
         * @return 返回一个Flux流，包含SimpleMeasurementValue对象，这些对象根据查询条件聚合了时间序列数据。
         */
        @Override
        public Flux<SimpleMeasurementValue> getValue(MeasurementParameter parameter) {
            // 根据参数中的format字段解析日期格式，若不存在则默认为"yyyy年MM月dd日"
            String format = parameter.getString("format").orElse("yyyy年MM月dd日");
            // 根据日期格式创建DateTimeFormatter
            DateTimeFormatter formatter = DateTimeFormat.forPattern(format);

            // 构建查询条件，查询最大"value"字段的值
            return AggregationQueryParam
                .of()
                .max("value")
                .filter(query ->
                            // 筛选名称为"gateway-server-session"的数据
                            query.where("name", "gateway-server-session")
                )
                .from(parameter
                          .getDate("from")
                          .orElse(Date.from(LocalDateTime
                                                .now()
                                                .plusDays(-30)
                                                .atZone(ZoneId.systemDefault())
                                                .toInstant()))) // 设置查询开始时间，若参数中不存在，则默认为当前时间的前30天
                .to(parameter.getDate("to").orElse(new Date())) // 设置查询结束时间，若参数中不存在，则默认为当前时间
                .groupBy(parameter.getInterval("time").orElse(Interval.ofDays(1)),
                         parameter.getString("format").orElse("yyyy年MM月dd日")) // 根据"time"参数指定的间隔和格式分组数据
                .limit(parameter.getInt("limit").orElse(10)) // 设置查询结果的限制数量，若参数中不存在，则默认为10
                .execute(timeSeriesManager.getService(DeviceTimeSeriesMetric.deviceMetrics())::aggregation) // 执行时间序列数据的聚合查询
                .map(data -> {
                    // 解析时间戳，若不存在则使用当前时间戳
                    long ts = data.getString("time")
                                  .map(time -> DateTime.parse(time, formatter).getMillis())
                                  .orElse(System.currentTimeMillis());
                    // 根据查询结果创建SimpleMeasurementValue对象
                    return SimpleMeasurementValue.of(
                        data.get("value").orElse(0),
                        data.getString("time", ""),
                        ts);
                })
                .sort(); // 对结果进行排序
        }

    }

    /**
     * 当前配置的元数据，用于描述设备的配置信息和状态。
     * 包括设备型号和设备状态两个配置项。
     */
    static ConfigMetadata currentMetadata = new DefaultConfigMetadata()
        // 添加设备型号配置项，设备型号为字符串类型
        .add("productId", "设备型号", "", new StringType())
        // 添加设备状态配置项，设备状态为枚举类型，包括在线、离线和未激活三种状态
        .add("state", "状态", "online", new EnumType()
            .addElement(EnumType.Element.of(DeviceState.online.getValue(), DeviceState.online.getText()))
            .addElement(EnumType.Element.of(DeviceState.offline.getValue(), DeviceState.offline.getText()))
            .addElement(EnumType.Element.of(DeviceState.notActive.getValue(), DeviceState.notActive.getText()))
        );
    //当前设备数量
    class CurrentNumberOfDeviceDimension implements MeasurementDimension {

        @Override
        public DimensionDefinition getDefinition() {
            return CommonDimensionDefinition.current;
        }

        @Override
        public DataType getValueType() {
            return new IntType();
        }

        @Override
        public ConfigMetadata getParams() {
            return currentMetadata;
        }

        @Override
        public boolean isRealTime() {
            return false;
        }

        /**
         * 根据给定的测量参数获取测量值。
         *
         * 此方法通过查询特定产品ID和状态的设备实例数量来生成测量值。
         * 使用了Reactive编程风格，返回一个Mono对象，该对象代表了一个未来的测量值。
         *
         * @param parameter 测量参数，包含产品ID和设备状态等信息。
         * @return 返回一个Mono对象，该对象包含了一个测量值，该测量值是基于查询到的设备实例数量和当前时间戳生成的。
         */
        @Override
        public Mono<MeasurementValue> getValue(MeasurementParameter parameter) {
            // 创建查询，指定查询条件为产品ID和设备状态，然后计算查询结果的数量。
            return instanceService
                .createQuery()
                .and(DeviceInstanceEntity::getProductId, parameter.getString("productId").orElse(null))
                .and(DeviceInstanceEntity::getState, parameter.get("state", DeviceState.class).orElse(null))
                .count()
                // 将查询结果转换为MeasurementValue对象。MeasurementValue包含了查询结果的数量（值）和当前的时间戳。
                .map(val -> SimpleMeasurementValue.of(val, System.currentTimeMillis()));
        }

    }


}
