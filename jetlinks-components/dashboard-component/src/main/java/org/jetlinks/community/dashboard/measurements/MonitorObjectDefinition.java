package org.jetlinks.community.dashboard.measurements;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.community.dashboard.ObjectDefinition;

/**
 * 监控对象定义枚举，实现了ObjectDefinition接口。
 * 该枚举用于定义监控系统中可监控的对象类型。
 */
@Getter
@AllArgsConstructor
public enum MonitorObjectDefinition implements ObjectDefinition {

    // 定义了三种监控对象类型
    cpu("CPU"), // CPU监控
    memory("内存"), // 内存监控
    stats("运行状态"); // 运行状态监控

    // 枚举类型的私有名称字段
    private final String name;

    /**
     * 获取当前枚举实例的ID。
     * 该方法重写了ObjectDefinition接口中的getId方法。
     *
     * @return 返回当前枚举实例的名称。
     */
    @Override
    public String getId() {
        return name();
    }
}

