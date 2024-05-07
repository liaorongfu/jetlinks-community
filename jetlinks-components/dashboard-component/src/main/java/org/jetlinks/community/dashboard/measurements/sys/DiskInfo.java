package org.jetlinks.community.dashboard.measurements.sys;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * DiskInfo类用于表示磁盘信息，实现了MonitorInfo接口。
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DiskInfo implements MonitorInfo<DiskInfo> {
    private static final long serialVersionUID = 1L;

    /**
     * 磁盘总容量，单位为MB。
     */
    @Schema(description = "磁盘总容量,单位MB")
    private long total;

    /**
     * 磁盘可用容量，单位为MB。
     */
    @Schema(description = "磁盘可用容量,单位MB")
    private long free;

    /**
     * 计算磁盘使用率。
     *
     * @return 磁盘使用率，返回值为百分比形式的浮点数。
     */
    public float getUsage() {
        // 计算磁盘使用率并四舍五入
        return MonitorUtils.round(
            ((total - free) / (float) total) * 100
        );
    }

    /**
     * 合并两个磁盘信息对象。
     *
     * @param info 要合并的另一个磁盘信息对象。
     * @return 合并后的磁盘信息对象。
     */
    @Override
    public DiskInfo add(DiskInfo info) {
        // 总容量和可用容量相加
        return new DiskInfo(
            info.total + this.total,
            info.free + this.free
        );
    }

    /**
     * 对磁盘容量进行等分。
     *
     * @param num 分割的份数。
     * @return 分割后的磁盘信息对象。
     */
    @Override
    public DiskInfo division(int num) {
        // 总容量和可用容量除以份数
        return new DiskInfo(
            this.total / num,
            this.free / num
        );
    }
}

