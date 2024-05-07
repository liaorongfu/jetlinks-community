package org.jetlinks.community.gateway.monitor.measurements;

import org.jetlinks.community.dashboard.supports.StaticMeasurementProvider;
import org.jetlinks.community.timeseries.TimeSeriesManager;
import org.jetlinks.community.timeseries.query.Aggregation;
import org.springframework.stereotype.Component;

import static org.jetlinks.community.dashboard.MeasurementDefinition.of;

/**
 * DeviceGatewayMeasurementProvider类扩展了StaticMeasurementProvider，用于提供设备网关的测量数据。
 * 这个类通过构造函数接收一个TimeSeriesManager实例，并基于这个实例配置一系列的设备网关测量值。
 *
 * @param timeSeriesManager 时间序列管理器，用于管理和查询时间序列数据。
 */
@Component
public class DeviceGatewayMeasurementProvider extends StaticMeasurementProvider {

    /**
     * 构造函数初始化设备网关的测量指标。
     * 添加了包括连接数、创建连接数、拒绝连接数、断开连接数、接收消息数和发送消息数在内的多个测量值。
     * 测量值的聚合方式包括最大值（MAX）和总和（SUM）。
     *
     * @param timeSeriesManager 时间序列管理器，用于配置测量指标。
     */
    public DeviceGatewayMeasurementProvider(TimeSeriesManager timeSeriesManager) {
        // 基于GatewayDashboardDefinition和GatewayObjectDefinition初始化父类
        super(GatewayDashboardDefinition.gatewayMonitor, GatewayObjectDefinition.deviceGateway);

        // 添加连接数的测量指标，采用最大值聚合方式
        addMeasurement(new DeviceGatewayMeasurement(of("connection", "连接数"), "value", Aggregation.MAX, timeSeriesManager));

        // 添加创建连接数、拒绝连接数、断开连接数的测量指标，都采用总和聚合方式
        addMeasurement(new DeviceGatewayMeasurement(of("connected", "创建连接数"), "count", Aggregation.SUM, timeSeriesManager));
        addMeasurement(new DeviceGatewayMeasurement(of("rejected", "拒绝连接数"), "count", Aggregation.SUM, timeSeriesManager));
        addMeasurement(new DeviceGatewayMeasurement(of("disconnected", "断开连接数"), "count", Aggregation.SUM, timeSeriesManager));

        // 添加接收消息数和发送消息数的测量指标，都采用总和聚合方式
        addMeasurement(new DeviceGatewayMeasurement(of("received_message", "接收消息数"), "count", Aggregation.SUM, timeSeriesManager));
        addMeasurement(new DeviceGatewayMeasurement(of("sent_message", "发送消息数"), "count", Aggregation.SUM, timeSeriesManager));
    }
}

