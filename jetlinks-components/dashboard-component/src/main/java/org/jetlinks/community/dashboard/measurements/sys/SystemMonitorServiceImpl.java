package org.jetlinks.community.dashboard.measurements.sys;

import org.jetlinks.community.dashboard.measurements.SystemMonitor;
import reactor.core.publisher.Mono;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * 系统监控服务的实现类，提供关于CPU、内存和磁盘的实时信息。
 */
public class SystemMonitorServiceImpl implements SystemMonitorService {

    // 获取内存管理 bean 实例
    private final static MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

    // 定义1MB的字节大小
    private final static int MB = 1024 * 1024;

    /**
     * 获取系统的综合信息，包括CPU、内存和磁盘信息。
     *
     * @return 返回包含系统CPU、内存和磁盘信息的SystemInfo对象的Mono。
     */
    @Override
    public Mono<SystemInfo> system() {
        // 同时获取CPU、内存和磁盘信息，然后映射为SystemInfo对象
        return Mono
            .zip(
                cpu(),
                memory(),
                disk()
            )
            .map(tp3 -> SystemInfo.of(tp3.getT1(), tp3.getT2(), tp3.getT3()));
    }

    /**
     * 获取磁盘信息。
     *
     * @return 返回包含磁盘总量和可用量的DiskInfo对象的Mono。
     */
    @Override
    public Mono<DiskInfo> disk() {
        // 遍历所有根目录，累加磁盘总空间和可用空间
        long total = 0, usable = 0;
        for (File file : File.listRoots()) {
            total += file.getTotalSpace();
            usable += file.getUsableSpace();
        }
        DiskInfo diskInfo = new DiskInfo();
        diskInfo.setTotal(total / MB); // 转换为MB
        diskInfo.setFree(usable / MB); // 转换为MB
        return Mono.just(diskInfo);
    }

    /**
     * 获取内存信息，包括系统内存和JVM内存。
     *
     * @return 返回包含系统内存和JVM内存信息的MemoryInfo对象的Mono。
     */
    public Mono<MemoryInfo> memory() {
        MemoryInfo info = new MemoryInfo();
        // 设置系统总内存和可用内存
        info.setSystemTotal((long) SystemMonitor.totalSystemMemory.value());
        info.setSystemFree((long) SystemMonitor.freeSystemMemory.value());

        // 获取JVM堆内存和非堆内存使用情况
        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
        // 计算非堆内存最大值，若为0，则使用系统总内存
        long nonHeapMax = (nonHeap.getMax() > 0 ? nonHeap.getMax() / MB : info.getSystemTotal());

        // 设置JVM堆内存和非堆内存的免费和总量
        info.setJvmHeapFree((heap.getMax() - heap.getUsed()) / MB);
        info.setJvmHeapTotal(heap.getMax() / MB);
        info.setJvmNonHeapFree(nonHeapMax - nonHeap.getUsed() / MB);
        info.setJvmNonHeapTotal(nonHeapMax);
        return Mono.just(info);
    }

    /**
     * 获取CPU信息，包括系统CPU和JVM CPU使用率。
     *
     * @return 返回包含系统CPU使用率和JVM CPU使用率的CpuInfo对象的Mono。
     */
    public Mono<CpuInfo> cpu() {
        CpuInfo info = new CpuInfo();
        // 设置系统和JVM的CPU使用率
        info.setSystemUsage(MonitorUtils.round((float) (SystemMonitor.systemCpuUsage.value())));
        info.setJvmUsage(MonitorUtils.round((float) (SystemMonitor.jvmCpuUsage.value())));
        return Mono.just(info);
    }
}

