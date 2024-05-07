package org.jetlinks.community.gateway;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.core.device.DeviceConfigKey;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.device.session.DeviceSessionManager;
import org.jetlinks.core.message.*;
import org.jetlinks.core.message.state.DeviceStateCheckMessage;
import org.jetlinks.core.message.state.DeviceStateCheckMessageReply;
import org.jetlinks.core.server.session.ChildrenDeviceSession;
import org.jetlinks.core.server.session.DeviceSession;
import org.jetlinks.core.server.session.KeepOnlineSession;
import org.jetlinks.core.server.session.LostDeviceSession;
import org.jetlinks.community.PropertyConstants;
import org.jetlinks.supports.server.DecodedClientMessageHandler;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 设备网关消息处理,会话管理工具类,用于统一封装对设备消息和会话的处理逻辑
 *
 * @author zhouhao
 * @see DeviceRegistry
 * @see DecodedClientMessageHandler
 * @since 1.5
 */
@AllArgsConstructor
@Getter
public class DeviceGatewayHelper {

    // 设备注册表
    private final DeviceRegistry registry;
    // 设备会话管理器
    private final DeviceSessionManager sessionManager;
    // 解码客户端消息处理器
    private final DecodedClientMessageHandler messageHandler;

    /**
     * 创建一个Consumer，用于设置设备会话的保持活动状态的超时时间。
     *
     * @param msg 设备消息，包含可选的保持在线超时时间。
     * @param timeoutSupplier 一个提供默认超时时间的Supplier，当消息中没有指定超时时使用。
     * @return 返回一个Consumer，该Consumer接受一个DeviceSession对象，并根据消息中的指定或默认值设置会话的保持活动状态超时时间。
     */
    public static Consumer<DeviceSession> applySessionKeepaliveTimeout(DeviceMessage msg, Supplier<Duration> timeoutSupplier) {
        return session -> {
            // 尝试从消息头中获取保持在线的超时时间，如果不存在则使用默认超时时间
            Integer timeout = msg.getHeaderOrElse(Headers.keepOnlineTimeoutSeconds, () -> null);
            if (null != timeout) {
                // 如果消息中指定了超时时间，则使用该时间设置会话的保持活动状态超时
                session.setKeepAliveTimeout(Duration.ofSeconds(timeout));
            } else {
                // 如果消息中没有指定超时时间，尝试获取默认超时时间
                Duration defaultTimeout = timeoutSupplier.get();
                if (null != defaultTimeout) {
                    // 使用获取到的默认超时时间设置会话的保持活动状态超时
                    session.setKeepAliveTimeout(defaultTimeout);
                }
            }
        };
    }

    /**
     * 处理设备消息的函数。
     *
     * @param message 设备消息，是处理的核心数据。
     * @param sessionBuilder 一个函数，用于根据设备操作员构建设备会话。
     * @return 返回一个包含设备操作员的Mono对象，代表异步处理的结果。
     */
    public Mono<DeviceOperator> handleDeviceMessage(DeviceMessage message,
                                                    Function<DeviceOperator, DeviceSession> sessionBuilder) {

        // 调用另一个重载的handleDeviceMessage方法，进行消息处理
        return handleDeviceMessage(message,
                                   sessionBuilder,
                                   (ignore) -> {
                                   },
                                   () -> {
                                   });
    }


    /**
     * 处理设备消息的重载方法，此方法主要负责处理来自设备的消息，
     * 并通过提供的会话构造器和回调来管理设备会话及处理设备不存在的情况。
     *
     * @param message                设备发送的消息，包含设备信息和消息内容。
     * @param sessionBuilder         一个函数接口，用于在设备会话不存在时构建新的设备会话。
     * @param sessionConsumer        一个消费者接口，用于在处理会话时自定义会话的行为，例如重置连接信息。
     * @param deviceNotFoundCallback 一个运行时接口，当设备未找到时调用此回调。
     *                                此处使用 lambda 表达式将 deviceNotFoundCallback 包装为 Mono。
     * @return 返回一个设备操作接口的 Mono 对象，该接口用于对设备进行各种操作。
     */
    public Mono<DeviceOperator> handleDeviceMessage(DeviceMessage message,
                                                    Function<DeviceOperator, DeviceSession> sessionBuilder,
                                                    Consumer<DeviceSession> sessionConsumer,
                                                    Runnable deviceNotFoundCallback) {

        // 通过 lambda 表达式将 deviceNotFoundCallback 包装为 Mono<>, 以便在异步操作中使用
        return handleDeviceMessage(message, sessionBuilder, sessionConsumer, () -> Mono.fromRunnable(deviceNotFoundCallback));
    }


    /**
     * 处理子设备消息的函数。
     * 该方法首先检查消息是否应该被忽略，例如设备状态检查消息、断开设备连接的消息或特定头部标识的消息。
     * 若消息不被忽略，则根据消息类型处理子设备的状态，如上线、下线或注册，并相应地更新或创建会话。
     *
     * @param deviceId 父设备的ID。
     * @param children 子设备的消息。
     * @return Mono<Void> 表示异步操作完成的空Mono。
     */
    private Mono<Void> handleChildrenDeviceMessage(String deviceId, DeviceMessage children) {
        // 检查是否应该忽略该消息
        //这些消息属于状态管理,通常是用来自定义子设备状态的,所以这些消息都忽略处理会话
        if (deviceId == null
            || children instanceof DeviceStateCheckMessage
            || children instanceof DeviceStateCheckMessageReply
            || children instanceof DisconnectDeviceMessage
            || children instanceof DisconnectDeviceMessageReply
            || children.getHeaderOrDefault(Headers.ignoreSession)) {
            return Mono.empty();
        }

        // 忽略子设备回复失败的消息
        if (children instanceof DeviceMessageReply) {
            DeviceMessageReply reply = ((DeviceMessageReply) children);
            if (!reply.isSuccess()) {
                return Mono.empty();
            }
        }

        String childrenId = children.getDeviceId();

        // 处理子设备下线或注销的消息
        if (children instanceof DeviceOfflineMessage || children instanceof DeviceUnRegisterMessage) {
            //注销会话,这里子设备可能会收到多次离线消息
            //注销会话一次离线,消息网关转发子设备消息一次
            return sessionManager
                .remove(childrenId, removeSessionOnlyLocal(children))
                .doOnNext(total -> {
                    // 标记离线消息，避免重复处理
                    if (total > 0 && children instanceof DeviceOfflineMessage) {
                        children.addHeader(Headers.ignore, true);
                    }
                })
                .then();
        } else {
            // 处理子设备上线的消息
            if (children instanceof DeviceOnlineMessage) {
                children.addHeader(Headers.ignore, true);
            }

            // 处理会话，包括创建或更新子设备会话
            Mono<DeviceSession> sessionHandler = sessionManager
                .getSession(deviceId)
                .flatMap(parentSession -> this
                    .createOrUpdateSession(childrenId,
                                           children,
                                           child -> Mono.just(new ChildrenDeviceSession(childrenId, parentSession, child)),
                                           Mono::empty)
                    .doOnNext(session -> {
                        // 检查并更新网关会话
                        if (session.isWrapFrom(ChildrenDeviceSession.class)) {
                            ChildrenDeviceSession childrenSession = session.unwrap(ChildrenDeviceSession.class);
                            //网关发生变化,替换新的上级会话
                            if (!Objects.equals(deviceId, childrenSession.getParent().getDeviceId())) {
                                childrenSession.replaceWith(parentSession);
                            }
                        }
                    }));

            // 处理子设备注册逻辑
            if (isDoRegister(children)) {
                return this
                    .getDeviceForRegister(children.getDeviceId())
                    .flatMap(device -> device
                        //没有配置状态自管理才自动上线
                        .getSelfConfig(DeviceConfigKey.selfManageState)
                        .defaultIfEmpty(false)
                        .filter(Boolean.FALSE::equals))
                    .flatMap(ignore -> sessionHandler)
                    .then();
            }
            return sessionHandler.then();
        }
    }


    /**
     * 处理设备消息的函数。
     *
     * @param message 接收到的设备消息，类型可以是多种特定的设备消息。
     * @param sessionBuilder 一个函数，用于根据设备操作者构建设备会话。
     * @param sessionConsumer 一个函数，用于消费设备会话。
     * @param deviceNotFoundCallback 当设备未找到时，提供一个回调供应Mono<DeviceOperator>。
     * @return 返回一个包含设备操作者的Mono<DeviceOperator>流。
     */
    public Mono<DeviceOperator> handleDeviceMessage(DeviceMessage message,
                                                    Function<DeviceOperator, Mono<DeviceSession>> sessionBuilder,
                                                    Function<DeviceSession, Mono<Void>> sessionConsumer,
                                                    Supplier<Mono<DeviceOperator>> deviceNotFoundCallback) {
        String deviceId = message.getDeviceId();
        if (!StringUtils.hasText(deviceId)) {
            return Mono.empty(); // 如果设备ID为空，则直接返回空的Mono。
        }
        Mono<DeviceOperator> then = null;
        boolean doHandle = true;

        // 根据不同的消息类型处理消息。
        // 包括子设备消息、子设备消息回复、设备离线消息、设备上线消息。
        if (message instanceof ChildDeviceMessage) {
            // 处理子设备消息。
            DeviceMessage childrenMessage = (DeviceMessage) ((ChildDeviceMessage) message).getChildDeviceMessage();
            then = handleChildrenDeviceMessage(deviceId, childrenMessage)
                .then(registry.getDevice(deviceId));
        }
        else if (message instanceof ChildDeviceMessageReply) {
            // 处理子设备消息回复。
            DeviceMessage childrenMessage = (DeviceMessage) ((ChildDeviceMessageReply) message).getChildDeviceMessage();
            then = handleChildrenDeviceMessage(deviceId, childrenMessage)
                .then(registry.getDevice(deviceId));
        }
        else if (message instanceof DeviceOfflineMessage) {
            // 处理设备离线消息。
            return sessionManager
                .remove(deviceId, removeSessionOnlyLocal(message))
                .flatMap(l -> {
                    if (l == 0) {
                        return registry
                            .getDevice(deviceId)
                            .flatMap(device -> handleMessage(device, message));
                    }
                    return Mono.empty();
                })
                .then(registry.getDevice(deviceId))
                .contextWrite(Context.of(DeviceMessage.class, message));
        }
        else if (message instanceof DeviceOnlineMessage) {
            // 处理设备上线消息，判断是否强制处理。
            doHandle = message
                .getHeader(Headers.force)
                .orElse(false);
        }

        if (then == null) {
            // 如果没有特定的消息处理，则默认获取设备信息。
            then = registry.getDevice(deviceId);
        }

        // 检查是否忽略会话管理，防止会话冲突。
        if (message.getHeaderOrDefault(Headers.ignoreSession)) {
            if (!isDoRegister(message)) {
                return handleMessage(null, message)
                    .then(then);
            }
            return then;
        }

        // 如果需要处理消息，则在处理后继续。
        if (doHandle) {
            then = handleMessage(null, message)
                .then(then);
        }

        // 创建或更新设备会话，然后消费会话，最后返回设备操作者。
        return this
            .createOrUpdateSession(deviceId, message, sessionBuilder, deviceNotFoundCallback)
            .flatMap(sessionConsumer)
            .then(then)
            .contextWrite(Context.of(DeviceMessage.class, message));
    }

    /**
     * 处理设备消息的函数。
     *
     * @param device 设备操作对象，代表需要操作的设备。
     * @param message 消息对象，代表需要处理的消息。
     * @return Mono<Void> 异步操作，表示处理过程。当处理完成后，不返回任何结果。
     */
    private Mono<Void> handleMessage(DeviceOperator device, Message message) {
        return messageHandler
            .handleMessage(device, message) // 调用消息处理器处理消息
            .flatMap(ignore -> Mono.empty()); // 转换为empty Mono，以减少可能触发的discard操作
    }


    /**
     * 创建或更新设备会话。
     *
     * @param deviceId 设备ID，用于标识设备。
     * @param message 设备消息，包含设备发送的数据或指令。
     * @param sessionBuilder 一个函数，用于根据设备操作员构建设备会话。
     * @param deviceNotFoundCallback 当设备未找到时，提供一个回调Supplier来生成Mono<DeviceOperator>。
     * @return 返回一个包含DeviceSession的Mono，表示设备的会话信息。
     */
    private Mono<DeviceSession> createOrUpdateSession(String deviceId,
                                                      DeviceMessage message,
                                                      Function<DeviceOperator, Mono<DeviceSession>> sessionBuilder,
                                                      Supplier<Mono<DeviceOperator>> deviceNotFoundCallback) {
        // 尝试获取现有会话，并检查其是否活跃
        return sessionManager
            .getSession(deviceId, false)
            .filterWhen(DeviceSession::isAliveAsync) // 筛选存活的会话
            .map(old -> {
                // 判断是否需要更新会话，如果需要则更新
                if (needUpdateSession(old, message)) {
                    return sessionManager
                        .compute(deviceId, null, session -> updateSession(session, message, sessionBuilder));
                }
                // 如果不需要更新会话，则进行会话保持活动操作
                applySessionKeepaliveTimeout(message, old);
                old.keepAlive();
                return Mono.just(old);
            })
            // 如果不存在当前会话，则尝试创建或更新会话
            .defaultIfEmpty(Mono.defer(() -> sessionManager
                .compute(deviceId,
                         createNewSession(
                             deviceId,
                             message,
                             sessionBuilder,
                             () -> {
                                 // 设备注册逻辑处理
                                 if (isDoRegister(message)) {
                                     return this
                                         .handleMessage(null, message)
                                         // 在设备注册失败后延迟2秒尝试重新获取设备并上线
                                         .then(Mono.delay(Duration.ofSeconds(2)))
                                         .then(registry.getDevice(deviceId));
                                 }
                                 // 如果提供了设备未找到的回调，则执行该回调
                                 if (deviceNotFoundCallback != null) {
                                     return deviceNotFoundCallback.get();
                                 }
                                 // 默认情况返回空Mono
                                 return Mono.empty();
                             }),
                         session -> updateSession(session, message, sessionBuilder))))
            .flatMap(Function.identity()); // 扁平化处理以确保流程的连贯性
    }


    /**
     * 尝试从设备注册表中获取指定ID的设备。如果设备不存在，则延迟2秒后再次尝试获取。
     * 这个方法主要用于设备注册过程中，当第一次尝试获取设备失败时，通过延迟给予注册流程一定时间来异步处理注册，以期望在第二次尝试时能够获取到设备。
     *
     * @param deviceId 设备的唯一标识符。
     * @return 返回一个包含设备操作者的Mono对象。如果设备不存在且第一次获取失败，则延迟2秒后再次尝试获取。
     */
    private Mono<DeviceOperator> getDeviceForRegister(String deviceId) {
        return registry
            // 尝试从注册表中获取设备
            .getDevice(deviceId)
            // 如果设备不存在（即空Mono），则进行延迟重试
            .switchIfEmpty(Mono.defer(() -> Mono
                // 延迟2秒以等待异步注册过程完成
                .delay(Duration.ofSeconds(2))
                // 之后再次尝试获取设备
                .then(registry.getDevice(deviceId))));
    }


    /**
     * 创建一个新的设备会话。
     *
     * @param deviceId 设备ID，用于标识要创建会话的设备。
     * @param message 设备消息，包含与设备会话创建相关的数据和指令。
     * @param sessionBuilder 一个函数，接受一个设备操作者对象，返回一个设备会话的Mono对象。
     * @param deviceNotFoundCallback 当找不到指定设备时，提供一个回调函数来创建或获取设备操作者。
     * @return 返回一个包含设备会话的Mono对象。如果设备在线，则会创建一个新的会话；如果设备不在线，会通过设备未找到回调来尝试获取或创建设备操作者，并建立会话。
     */
    private Mono<DeviceSession> createNewSession(String deviceId,
                                                 DeviceMessage message,
                                                 Function<DeviceOperator, Mono<DeviceSession>> sessionBuilder,
                                                 Supplier<Mono<DeviceOperator>> deviceNotFoundCallback) {
        // 尝试从设备注册表中获取设备，如果设备不存在则调用设备未找到的回调函数
        return registry
            .getDevice(deviceId)
            .switchIfEmpty(Mono.defer(deviceNotFoundCallback))
            .flatMap(device -> sessionBuilder
                .apply(device)
                .map(newSession -> {
                    // 当消息头中标识需要保持在线时，创建一个保持在线的会话实例
                    if (message.getHeader(Headers.keepOnline).orElse(false)) {
                        int timeout = message.getHeaderOrDefault(Headers.keepOnlineTimeoutSeconds);
                        // 设置保持在线的时间
                        newSession = new KeepOnlineSession(newSession, Duration.ofSeconds(timeout));
                    }
                    return newSession;
                }));
    }


    /**
     * 更新设备会话。
     * 如果给定的设备会话仍然存活，则使用提供的函数更新会话。
     * 如果会话不存活，则基于传入的消息创建一个新的会话。
     *
     * @param session 当前的设备会话实例。
     * @param message 设备发送的消息。
     * @param sessionBuilder 一个函数，用于根据设备操作构建或更新设备会话。
     * @return 返回一个包含更新或新创建的设备会话的Mono实例。
     */
    private Mono<DeviceSession> updateSession(DeviceSession session,
                                              DeviceMessage message,
                                              Function<DeviceOperator, Mono<DeviceSession>> sessionBuilder) {

        return session
            .isAliveAsync() // 检查当前会话是否仍然存活
            .flatMap(alive -> {
                // 如果会话存活，则更新会话
                if (alive) {
                    return updateSession0(session, message, sessionBuilder);
                }
                // 如果会话不存活，则创建一个新的会话
                return createNewSession(message.getDeviceId(), message, sessionBuilder, Mono::empty);
            });
    }


    /**
     * 更新设备会话逻辑。
     * <p>
     * 该方法首先检查消息是否指定设备应保持在线状态，并根据之前的会话状态决定是否需要替换会话以保持在线。
     * 若检测到之前的会话为非保持在线状态，而消息中指定需要保持在线，则会创建一个新的KeepOnlineSession来替代。
     * <p>
     * 同时，如果当前会话是KeepOnline类型且检测到会话已经丢失（如服务重启），则会通过提供的sessionBuilder函数重建会话，
     * 以恢复设备的下行通信能力。
     * <p>
     * 最后，更新会话的保活计时，并在必要时返回新的会话Mono对象。
     *
     * @param session 当前设备会话实例。
     * @param message 收到的设备消息，可能包含保持在线的指令和超时设置。
     * @param sessionBuilder 一个函数，用于根据设备操作员构建新的设备会话Mono对象。
     * @return 返回更新后的设备会话Mono对象。如果不存在会话更新，则返回原会话的Mono对象；如果存在会话更新，则返回新会话的Mono对象。
     */
    private Mono<DeviceSession> updateSession0(DeviceSession session,
                                               DeviceMessage message,
                                               Function<DeviceOperator, Mono<DeviceSession>> sessionBuilder) {
        Mono<DeviceSession> after = null;

        // 检查消息是否指定保持在线，并处理会话替换逻辑
        if (isNewKeeOnline(session, message)) {
            Integer timeoutSeconds = message.getHeaderOrDefault(Headers.keepOnlineTimeoutSeconds);
            // 替换session为保持在线状态的会话
            session = new KeepOnlineSession(session, Duration.ofSeconds(timeoutSeconds));
        }

        // 检查是否需要因为KeepOnline会话丢失而重建会话
        if (isKeeOnlineLost(session)) {
            Integer timeoutSeconds = message.getHeaderOrDefault(Headers.keepOnlineTimeoutSeconds);
            // 通过提供的sessionBuilder构建新的会话，并设置为保持在线状态
            after = sessionBuilder
                .apply(session.getOperator())
                .map(newSession -> new KeepOnlineSession(newSession, Duration.ofSeconds(timeoutSeconds)));
        }

        // 应用会话保持活动的超时设置
        applySessionKeepaliveTimeout(message, session);
        // 触发会话保活操作
        session.keepAlive();

        // 根据是否存在会话更新返回相应的Mono对象
        return after == null
            ? Mono.just(session)
            : after;
    }


    /**
     * 为设备会话应用保持活跃的超时设置。
     * 此方法将从设备消息中提取保持在线的超时时间（以秒为单位），如果提取成功，则将其应用到设备会话中。
     *
     * @param msg 设备消息，包含可能的保持在线超时设置。
     * @param session 设备会话，将根据消息中的设置更新保持活跃的超时时间。
     */
    private static void applySessionKeepaliveTimeout(DeviceMessage msg, DeviceSession session) {
        // 尝试从消息头中获取保持在线的超时时间，如果不存在则返回null
        Integer timeout = msg.getHeaderOrElse(Headers.keepOnlineTimeoutSeconds, () -> null);
        if (null != timeout) {
            // 如果超时时间不为null，将其设置为会话的保持活跃超时时间
            session.setKeepAliveTimeout(Duration.ofSeconds(timeout));
        }
    }


    //是否只移除当前节点中的会话,默认false,表示下行则移除整个集群的会话.
    //设置addHeader("clearAllSession",false); 表示只移除本地会话
    private boolean removeSessionOnlyLocal(DeviceMessage message) {
        return message
            .getHeader(Headers.clearAllSession)
            .map(val -> !val)
            .orElse(false);
    }

    //判断是否需要更新会话
    private static boolean needUpdateSession(DeviceSession session, DeviceMessage message) {
        return isNewKeeOnline(session, message) || isKeeOnlineLost(session);
    }

    //判断是否为新的保持在线消息
    private static boolean isNewKeeOnline(DeviceSession session, DeviceMessage message) {
        return message.getHeader(Headers.keepOnline).orElse(false) && !(session instanceof KeepOnlineSession);
    }

    //判断保持在线的会话是否以及丢失(服务重启后可能出现)
    private static boolean isKeeOnlineLost(DeviceSession session) {
        if (!session.isWrapFrom(KeepOnlineSession.class)) {
            return false;
        }
        return session.isWrapFrom(LostDeviceSession.class)
            || !session.unwrap(KeepOnlineSession.class).getParent().isAlive();
    }

    //判断是否为设备注册
    private static boolean isDoRegister(DeviceMessage message) {
        return message instanceof DeviceRegisterMessage
            && message.getHeader(PropertyConstants.deviceName).isPresent()
            && message.getHeader(PropertyConstants.productId).isPresent();
    }


    /**
     * 处理设备消息
     *
     * @param message                设备消息
     * @param sessionBuilder         会话构造器,在会话不存在时,创建会话
     * @param sessionConsumer        会话自定义回调,处理会话时用来自定义会话,比如重置连接信息
     * @param deviceNotFoundCallback 设备不存在的监听器回调
     * @return 设备操作接口
     */
    public Mono<DeviceOperator> handleDeviceMessage(DeviceMessage message,
                                                    Function<DeviceOperator, DeviceSession> sessionBuilder,
                                                    Consumer<DeviceSession> sessionConsumer,
                                                    Supplier<Mono<DeviceOperator>> deviceNotFoundCallback) {
        return this
            .handleDeviceMessage(
                message,
                device -> Mono.justOrEmpty(sessionBuilder.apply(device)),
                session -> {
                    sessionConsumer.accept(session);
                    return Mono.empty();
                },
                deviceNotFoundCallback
            );

    }


}
