package org.jetlinks.community.gateway;

/**
 * GatewayState枚举定义了网关的不同状态。
 * 它用于表示网关在不同阶段的状态，便于状态管理和控制。
 */
public enum GatewayState {
    starting, // 表示网关正在启动
    started,  // 表示网关已经启动完成，处于运行状态
    paused,   // 表示网关已经暂停运行
    shutdown  // 表示网关已经关闭
}

