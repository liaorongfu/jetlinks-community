package org.jetlinks.community.device.measurements;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.community.dashboard.DashboardDefinition;

/**
 * 设备仪表盘定义枚举
 * 该枚举实现了DashboardDefinition接口，用于定义设备仪表盘的唯一标识和名称。
 * @author Rabbuse
 */
@Getter
@AllArgsConstructor
public enum DeviceDashboardDefinition implements DashboardDefinition {

    /**
     * 设备仪表盘的唯一实例
     * id: "device"，代表设备仪表盘的标识
     * name: "设备信息"，代表设备仪表盘的名称
     */
    instance("device", "设备信息");

    // 设备仪表盘的唯一标识
    private String id;

    // 设备仪表盘的名称
    private String name;
}

