package org.jetlinks.community.dashboard.measurements.sys;

import reactor.core.publisher.Mono;

/**
 * 系统监控服务接口，提供获取系统信息、内存信息、CPU信息和磁盘信息的方法。
 */
public interface SystemMonitorService {

    /**
     * 获取系统信息。
     *
     * @return 返回包含系统信息的Mono对象。
     */
    Mono<SystemInfo> system();

    /**
     * 获取内存信息。
     *
     * @return 返回包含内存信息的Mono对象。
     */
    Mono<MemoryInfo> memory();

    /**
     * 获取CPU信息。
     *
     * @return 返回包含CPU信息的Mono对象。
     */
    Mono<CpuInfo> cpu();

    /**
     * 获取磁盘信息。
     *
     * @return 返回包含磁盘信息的Mono对象。
     */
    Mono<DiskInfo> disk();
}

