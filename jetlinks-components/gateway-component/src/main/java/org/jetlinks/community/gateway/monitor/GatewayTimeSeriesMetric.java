package org.jetlinks.community.gateway.monitor;

import org.jetlinks.community.timeseries.TimeSeriesMetric;

/**
 * GatewayTimeSeriesMetric 接口定义了与网关时间序列指标相关的操作。
 * 这个接口主要用于定义设备网关的监控指标。
 */
public interface GatewayTimeSeriesMetric {

    // 定义设备网关监控的指标名称常量
    String deviceGatewayMetric = "device_gateway_monitor";

    /**
     * 获取设备网关监控指标的静态方法。
     *
     * @return TimeSeriesMetric 返回一个基于设备网关监控指标的TimeSeriesMetric实例。
     */
    static TimeSeriesMetric deviceGatewayMetric(){
        // 创建并返回一个基于设备网关监控指标的TimeSeriesMetric实例
        return TimeSeriesMetric.of(deviceGatewayMetric);
    }
}

