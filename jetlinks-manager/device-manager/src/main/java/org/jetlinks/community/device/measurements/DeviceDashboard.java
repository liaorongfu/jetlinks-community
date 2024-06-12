package org.jetlinks.community.device.measurements;

import org.jetlinks.community.dashboard.Dashboard;
import org.jetlinks.community.dashboard.DashboardDefinition;

/**
 * 设备仪表盘接口，继承自Dashboard接口。
 * 该接口定义了设备仪表盘的具体行为，实现了从仪表盘定义到具体设备仪表盘定义的映射。
 */
public interface DeviceDashboard extends Dashboard {

    /**
     * 获取设备仪表盘的定义。
     * 该方法通过默认方法实现了对设备仪表盘定义的获取，避免了对具体实现的直接依赖。
     *
     * @return 返回设备仪表盘的定义实例。
     */
    @Override
    default DashboardDefinition getDefinition() {
        return DeviceDashboardDefinition.instance;
    }
}

