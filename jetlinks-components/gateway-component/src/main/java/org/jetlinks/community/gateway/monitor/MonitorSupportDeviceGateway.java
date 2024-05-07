package org.jetlinks.community.gateway.monitor;

/**
 * MonitorSupportDeviceGateway接口定义了监控支持设备网关的基本操作。
 * 该接口主要用于获取与设备相关的连接总数。
 */
public interface MonitorSupportDeviceGateway {

    /**
     * 获取设备的总连接数。
     * 该方法不接受任何参数。
     *
     * @return 返回设备的总连接数，类型为long。
     */
    long totalConnection();

}

