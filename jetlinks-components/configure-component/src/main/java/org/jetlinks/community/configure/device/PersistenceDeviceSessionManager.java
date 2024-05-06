package org.jetlinks.community.configure.device;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.MVStoreException;
import org.jetlinks.community.configure.cluster.Cluster;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.device.session.DeviceSessionEvent;
import org.jetlinks.core.rpc.RpcManager;
import org.jetlinks.core.server.session.DeviceSession;
import org.jetlinks.core.server.session.PersistentSession;
import org.jetlinks.supports.device.session.ClusterDeviceSessionManager;
import org.springframework.beans.BeansException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.util.Lazy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.io.File;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * 持久化设备会话管理器，扩展自ClusterDeviceSessionManager，实现了CommandLineRunner和ApplicationContextAware接口。
 * 用于管理设备会话，并将这些会话信息持久化到磁盘中。
 */
@Slf4j
public class PersistenceDeviceSessionManager extends ClusterDeviceSessionManager implements CommandLineRunner, ApplicationContextAware {
    // 设备注册表的提供者
    private Supplier<DeviceRegistry> registry;

    // 用于存储会话信息的持久化仓库
    private MVMap<String, PersistentSessionEntity> repository;

    // 仓库文件的路径
    @Getter
    @Setter
    private String filePath;

    // 数据刷新间隔
    @Getter
    @Setter
    private Duration flushInterval = Duration.ofMinutes(10);

    /**
     * 构造函数，初始化一个持久化设备会话管理器。
     *
     * @param rpcManager RPC管理器
     */
    public PersistenceDeviceSessionManager(RpcManager rpcManager) {
        super(rpcManager);
    }

    /**
     * 初始化存储空间，如果存储文件不存在，则创建它。
     *
     * @param file 存储文件的路径
     * @return 初始化后的存储映射
     */
    static MVMap<String, PersistentSessionEntity> initStore(String file) {
        // 确保存储文件的目录存在
        File f = new File(file);
        if (!f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }
        // 创建存储映射
        Supplier<MVMap<String, PersistentSessionEntity>>
            builder = () -> {
            MVStore store = new MVStore.Builder()
                .fileName(file)
                .cacheSize(1)
                .open();
            return store.openMap("device-session");
        };
        try {
            return builder.get();
        } catch (MVStoreException e) {
            log.warn("加载会话失败，删除并重新初始化。", file, e);
            f.delete();
            return builder.get();
        }
    }

    /**
     * 初始化函数，创建或加载会话存储，设置数据刷新任务。
     */
    @Override
    public void init() {
        super.init();
        // 设置默认的文件路径
        if (filePath == null) {
            filePath = "./data/sessions-" + (Cluster
                .id()
                .replace(":", "_")
                .replace("/", ""));
        }
        repository = initStore(filePath);

        // 如果设置了刷新间隔，则启动定时刷新任务
        if (!flushInterval.isZero() && !flushInterval.isNegative()) {
            disposable.add(
                Flux
                    .interval(flushInterval)
                    .onBackpressureDrop()
                    .concatMap(ignore -> Flux
                        .fromIterable(localSessions.values())
                        .mapNotNull(ref -> {
                            if (ref.loaded != null && ref.loaded.isWrapFrom(PersistentSession.class)) {
                                return ref.loaded.unwrap(PersistentSession.class);
                            }
                            return null;
                        })
                        .as(this::tryPersistent), 1)
                    .subscribe()
            );
        }

        // 监听设备会话事件，移除已注销的持久化会话
        disposable.add(
            listenEvent(event -> {
                if (event.getType() == DeviceSessionEvent.Type.unregister
                    && event.getSession().isWrapFrom(PersistentSession.class)) {
                    return removePersistentSession(
                        event.getSession().unwrap(PersistentSession.class)
                    );
                }
                return Mono.empty();
            })
        );
    }

    /**
     * 关闭函数，持久化所有会话并关闭存储。
     */
    @Override
    public void shutdown() {
        super.shutdown();
        // 持久化所有本地会话
        Flux.fromIterable(localSessions.values())
            .filter(ref -> ref.loaded != null)
            .filter(ref -> ref.loaded.isWrapFrom(PersistentSession.class))
            .map(ref -> ref.loaded.unwrap(PersistentSession.class))
            .as(this::tryPersistent)
            .block();
        // 清理并关闭存储
        repository.store.compactMoveChunks();
        repository.store.close();
    }

    /**
     * 处理会话更新逻辑，如果新会话是持久化的，则尝试持久化。
     *
     * @param old 旧会话
     * @param newSession 新会话
     * @return 更新后的会话
     */
    @Override
    protected Mono<DeviceSession> handleSessionCompute(DeviceSession old, DeviceSession newSession) {
        if (old == newSession) {
            return Mono.just(newSession);
        }
        if ((old == null || !old.isWrapFrom(PersistentSession.class))
            && newSession.isWrapFrom(PersistentSession.class)) {
            return this
                .tryPersistent(Flux.just(newSession.unwrap(PersistentSession.class)))
                .thenReturn(newSession);
        }
        return super.handleSessionCompute(old, newSession);
    }

    /**
     * 尝试将一组持久化会话持久化到存储中。
     *
     * @param sessions 待持久化的会话流
     * @return 空的Mono
     */
    Mono<Void> tryPersistent(Flux<PersistentSession> sessions) {
        return sessions
            .flatMap(session -> PersistentSessionEntity.from(getCurrentServerId(), session, registry.get()))
            .distinct(PersistentSessionEntity::getId)
            .doOnNext(e -> {
                log.debug("持久化设备[{}]会话", e.getDeviceId());
                repository.put(e.getDeviceId(), e);
            })
            .onErrorResume(err -> {
                log.warn("持久化会话错误", err);
                return Mono.empty();
            })
            .then();
    }

    /**
     * 恢复一个持久化会话实体为设备会话。
     *
     * @param entity 持久化会话实体
     * @return 空的Mono
     */
    Mono<Void> resumeSession(PersistentSessionEntity entity) {
        return entity
            .toSession(registry.get())
            .doOnNext(session -> {
                log.debug("恢复会话[{}]", session.getDeviceId());
                localSessions.putIfAbsent(session.getDeviceId(),
                                          new DeviceSessionRef(session.getDeviceId(),
                                                               this,
                                                               session));
            })
            .onErrorResume((err) -> {
                log.debug("恢复会话[{}]错误", entity.getDeviceId(), err);
                return Mono.empty();
            })
            .then();
    }

    /**
     * 移除一个持久化会话。
     *
     * @param session 待移除的持久化会话
     * @return 空的Mono
     */
    Mono<Void> removePersistentSession(PersistentSession session) {
        repository.remove(session.getId());
        return Mono.empty();
    }

    /**
     * 命令行运行入口，用于恢复所有持久化会话。
     *
     * @param args 命令行参数
     * @throws Exception 可能抛出的异常
     */
    @Override
    public void run(String... args) throws Exception {
        // 恢复所有持久化会话
        Flux.fromIterable(repository.values())
            .flatMap(this::resumeSession)
            .subscribe();
    }

    /**
     * 设置应用上下文，并从中获取设备注册表的实例。
     *
     * @param applicationContext 应用上下文
     * @throws BeansException 可能的BeansException异常
     */
    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.registry = Lazy.of(() -> applicationContext.getBean(DeviceRegistry.class));
    }
}

