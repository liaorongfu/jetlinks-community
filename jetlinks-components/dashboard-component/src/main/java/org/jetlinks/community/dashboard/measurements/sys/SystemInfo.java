package org.jetlinks.community.dashboard.measurements.sys;

import lombok.*;

/**
 * SystemInfo 类代表了系统的相关信息，包括CPU、内存和磁盘信息。
 * 它实现了 MonitorInfo 接口，提供了对系统信息的添加和除法运算操作。
 */
@Getter
@Setter
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
@ToString
public class SystemInfo implements MonitorInfo<SystemInfo> {

    private CpuInfo cpu; // CPU信息
    private MemoryInfo memory; // 内存信息
    private DiskInfo disk; // 磁盘信息

    /**
     * 将两个 SystemInfo 对象的性能信息相加。
     * @param info 要添加的另一个 SystemInfo 对象
     * @return 返回一个新的 SystemInfo 对象，其性能信息为当前对象和参数对象的性能信息之和
     */
    @Override
    public SystemInfo add(SystemInfo info) {
        return new SystemInfo(
            this.cpu.add(info.cpu),
            this.memory.add(info.memory),
            this.disk.add(info.disk)
        );
    }

    /**
     * 对系统的CPU、内存和磁盘信息进行除法运算。
     * @param num 除数
     * @return 返回一个新的 SystemInfo 对象，其性能信息为当前对象的性能信息除以给定的除数
     */
    @Override
    public SystemInfo division(int num) {

        return new SystemInfo(
            this.cpu.division(num),
            this.memory.division(num),
            this.disk.division(num)
        );
    }
}

