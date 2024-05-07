package org.jetlinks.community.dashboard.measurements.sys;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * CPU信息类，用于存储和处理CPU的使用率信息。
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class CpuInfo implements MonitorInfo<CpuInfo> {
    private static final long serialVersionUID = 1L;

    /**
     * JVM进程CPU使用率，范围为0-100。
     */
    @Schema(description = "JVM进程CPU使用率,0-100")
    private float jvmUsage;

    /**
     * 系统CPU使用率，范围为0-100。
     */
    @Schema(description = "系统CPU使用率,0-100")
    private float systemUsage;

    /**
     * 合并两个CPU信息对象的使用率数据。
     * @param info 要合并的CPU信息对象。
     * @return 合并后的CPU信息对象。
     */
    @Override
    public CpuInfo add(CpuInfo info) {
        // 计算并返回合并后的CPU使用率
        return new CpuInfo(
            MonitorUtils.round(info.jvmUsage + this.jvmUsage),
            MonitorUtils.round(info.systemUsage + this.systemUsage)
        );
    }

    /**
     * 将当前CPU信息对象的使用率数值除以一个给定的整数。
     * @param num 用于除以的整数。
     * @return 除法运算后的CPU信息对象。
     */
    @Override
    public CpuInfo division(int num) {
        // 计算并返回除法运算后的CPU使用率
        return new CpuInfo(
            MonitorUtils.round(this.jvmUsage / num),
            MonitorUtils.round(this.systemUsage / num)
        );
    }

}

