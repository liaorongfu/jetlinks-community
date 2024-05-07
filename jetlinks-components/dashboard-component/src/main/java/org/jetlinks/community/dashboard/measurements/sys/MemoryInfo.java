package org.jetlinks.community.dashboard.measurements.sys;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * 内存信息类，用于监控和管理内存使用情况。
 * 提供了JVM堆内存、JVM堆外内存以及系统内存的使用信息。
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class MemoryInfo implements MonitorInfo<MemoryInfo> {
    private static final long serialVersionUID = 1L;

    // JVM堆内存总使用量，单位：MB
    @Schema(description = "JVM堆总内存,单位MB")
    private long jvmHeapTotal;

    // JVM堆内存可用量，单位：MB
    @Schema(description = "JVM堆可用内存,单位MB")
    private long jvmHeapFree;

    // JVM堆外内存总使用量，单位：MB
    @Schema(description = "JVM堆外总内存,单位MB")
    private long jvmNonHeapTotal;

    // JVM堆外内存可用量，单位：MB
    @Schema(description = "JVM堆外可用内存,单位MB")
    private long jvmNonHeapFree;

    // 系统总内存，单位：MB
    @Schema(description = "系统总内存,单位MB")
    private long systemTotal;

    // 系统可用内存，单位：MB
    @Schema(description = "系统可用内存,单位MB")
    private long systemFree;

    /**
     * 计算并返回JVM堆内存的使用率（百分比）。
     *
     * @return JVM堆内存使用率，保留两位小数。
     */
    public float getJvmHeapUsage() {
        return MonitorUtils.round(
            ((jvmHeapTotal - jvmHeapFree) / (float) jvmHeapTotal) * 100
        );
    }

    /**
     * 计算并返回JVM堆外内存的使用率（百分比）。
     *
     * @return JVM堆外内存使用率，保留两位小数。
     */
    public float getJvmNonHeapUsage() {
        return MonitorUtils.round(
            ((jvmNonHeapTotal - jvmNonHeapFree) / (float) jvmNonHeapTotal) * 100
        );
    }

    /**
     * 计算并返回系统内存的使用率（百分比）。
     *
     * @return 系统内存使用率，保留两位小数。
     */
    public float getSystemUsage() {
        return MonitorUtils.round(
            ((systemTotal - systemFree) / (float) systemTotal) * 100
        );
    }

    /**
     * 合并两个内存信息对象的内存使用数据。
     *
     * @param info 要合并的另一个内存信息对象。
     * @return 合并后的内存信息对象。
     */
    @Override
    public MemoryInfo add(MemoryInfo info) {
        return new MemoryInfo(
            info.jvmHeapTotal + this.jvmHeapTotal,
            info.jvmHeapFree + this.jvmHeapFree,
            info.jvmNonHeapTotal + this.jvmNonHeapTotal,
            info.jvmNonHeapFree + this.jvmNonHeapFree,
            info.systemTotal + this.systemTotal,
            info.systemFree + this.systemFree
        );
    }

    /**
     * 将当前内存信息对象的各内存使用量除以指定数值。
     *
     * @param num 除法运算的除数。
     * @return 计算后的内存信息对象。
     */
    @Override
    public MemoryInfo division(int num) {
        return new MemoryInfo(
            this.jvmHeapTotal / num,
            this.jvmHeapFree / num,
            this.jvmNonHeapTotal / num,
            this.jvmNonHeapFree / num,
            this.systemTotal / num,
            this.systemFree / num
        );
    }
}

