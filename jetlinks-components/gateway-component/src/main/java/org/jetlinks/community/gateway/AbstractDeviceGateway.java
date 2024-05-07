package org.jetlinks.community.gateway;

import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.gateway.monitor.DeviceGatewayMonitor;
import org.jetlinks.community.gateway.monitor.GatewayMonitors;
import org.jetlinks.core.message.Message;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;

/**
 * 提供一个设备网关的抽象基类，实现了设备网关接口。
 * 使用者可以通过继承这个抽象类来实现具体的设备网关功能。
 */
@Slf4j
public abstract class AbstractDeviceGateway implements DeviceGateway {
    // 使用AtomicReferenceFieldUpdater来更新网关状态，以支持并发控制
    private final static AtomicReferenceFieldUpdater<AbstractDeviceGateway, GatewayState>
        STATE = AtomicReferenceFieldUpdater.newUpdater(AbstractDeviceGateway.class, GatewayState.class, "state");

    private final String id; // 网关唯一标识

    // 用于状态变更通知的监听器列表，支持并发修改
    private final List<BiConsumer<GatewayState, GatewayState>> stateListener = new CopyOnWriteArrayList<>();

    // 网关当前状态，使用volatile关键字支持并发可见性
    private volatile GatewayState state = GatewayState.shutdown;

    // 网关监控实例，用于监控网关状态和指标
    protected final DeviceGatewayMonitor monitor;

    /**
     * 构造函数，初始化网关实例。
     * @param id 网关的唯一标识符。
     */
    public AbstractDeviceGateway(String id) {
        this.id = id;
        this.monitor = GatewayMonitors.getDeviceGatewayMonitor(id);
    }

    /**
     * 获取网关的ID。
     * @return 返回网关的唯一标识符。
     */
    @Override
    public final String getId() {
        return id;
    }

    /**
     * 当有消息时进行处理。
     * @return 返回一个空的Flux流。
     */
    @Override
    public Flux<Message> onMessage() {
        return Flux.empty();
    }

    /**
     * 网关启动函数，确保网关处于启动状态。
     * @return 返回一个空的Mono实例，表示异步启动操作完成。
     */
    @Override
    public final synchronized Mono<Void> startup() {
        // 如果当前状态为暂停，则切换到启动状态
        if (state == GatewayState.paused) {
            changeState(GatewayState.started);
            return Mono.empty();
        }
        // 如果已经在启动或正在启动，则直接返回
        if (state == GatewayState.started || state == GatewayState.starting) {
            return Mono.empty();
        }
        // 设置状态为正在启动，并执行实际的启动操作
        changeState(GatewayState.starting);
        return this
            .doStartup()
            .doOnSuccess(ignore -> changeState(GatewayState.started));
    }

    /**
     * 暂停网关操作。
     * @return 返回一个空的Mono实例，表示暂停操作完成。
     */
    @Override
    public final Mono<Void> pause() {
        changeState(GatewayState.paused);
        return Mono.empty();
    }

    /**
     * 关闭网关，确保网关状态为关闭。
     * @return 返回一个Mono实例，表示异步关闭操作完成。
     */
    @Override
    public final Mono<Void> shutdown() {
        // 通过原子操作设置状态为关闭，避免并发问题
        GatewayState old = STATE.getAndSet(this, GatewayState.shutdown);

        // 如果状态已经是关闭，则直接返回
        if (old == GatewayState.shutdown) {
            return Mono.empty();
        }
        // 执行实际的关闭操作，并更新状态为关闭
        changeState(GatewayState.shutdown);
        return doShutdown();
    }

    /**
     * 实现具体的关闭操作。
     * @return 返回一个Mono实例，表示关闭操作的完成。
     */
    protected abstract Mono<Void> doShutdown();

    /**
     * 实现具体的启动操作。
     * @return 返回一个Mono实例，表示启动操作的完成。
     */
    protected abstract Mono<Void> doStartup();

    /**
     * 更改网关状态，并通知所有状态监听器。
     * @param target 目标状态。
     */
    protected synchronized final void changeState(GatewayState target) {
        GatewayState old = STATE.getAndSet(this, target);
        // 如果新旧状态相同，则直接返回
        if (target == old) {
            return;
        }
        // 通知所有监听器状态变更
        for (BiConsumer<GatewayState, GatewayState> consumer : stateListener) {
            try {
                consumer.accept(old, this.state);
            } catch (Throwable error) {
                log.warn("fire gateway {} state listener error", getId(), error);
            }
        }
    }

    /**
     * 获取当前网关状态。
     * @return 返回当前的网关状态。
     */
    @Override
    public final GatewayState getState() {
        return state;
    }

    /**
     * 注册一个状态变更监听器。
     * @param listener 状态变更时要执行的消费者函数。
     */
    @Override
    public final void doOnStateChange(BiConsumer<GatewayState, GatewayState> listener) {
        stateListener.add(listener);
    }

    /**
     * 注册一个在网关关闭时要执行的资源清理操作。
     * @param disposable 要在关闭时清理的资源。
     */
    @Override
    public final void doOnShutdown(Disposable disposable) {
        DeviceGateway.super.doOnShutdown(disposable);
    }

    /**
     * 检查网关是否处于活动状态。
     * @return 如果网关处于启动或正在启动状态，则返回true，否则返回false。
     */
    @Override
    public final boolean isAlive() {
        return state == GatewayState.started || state == GatewayState.starting;
    }
}

