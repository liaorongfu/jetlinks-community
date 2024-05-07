package org.jetlinks.community.network.mqtt.gateway.device;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.gateway.AbstractDeviceGateway;
import org.jetlinks.community.gateway.DeviceGatewayHelper;
import org.jetlinks.community.network.mqtt.gateway.device.session.MqttConnectionSession;
import org.jetlinks.community.network.mqtt.server.MqttConnection;
import org.jetlinks.community.network.mqtt.server.MqttPublishing;
import org.jetlinks.community.network.mqtt.server.MqttServer;
import org.jetlinks.community.utils.ObjectMappers;
import org.jetlinks.community.utils.SystemUtils;
import org.jetlinks.core.ProtocolSupport;
import org.jetlinks.core.device.*;
import org.jetlinks.core.device.session.DeviceSessionManager;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.core.server.session.DeviceSession;
import org.jetlinks.core.server.session.KeepOnlineSession;
import org.jetlinks.core.trace.FluxTracer;
import org.jetlinks.core.trace.MonoTracer;
import org.jetlinks.supports.server.DecodedClientMessageHandler;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

import static org.jetlinks.core.trace.DeviceTracer.SpanKey;
import static org.jetlinks.core.trace.DeviceTracer.SpanName;

/**
 * MQTT 服务设备网关,用于通过内置的mqtt server来进行设备通信,接入设备到平台中.
 *
 * <pre>
 *     1. 监听Mqtt服务中的连接{@link MqttServer#handleConnection()}
 *     2. 使用{@link MqttConnection#getClientId()}作为设备ID,从设备注册中心中获取设备.
 *     3. 使用设备对应的协议{@link DeviceOperator#getProtocol()}来进行认证{@link ProtocolSupport#authenticate(AuthenticationRequest, DeviceOperator)}
 *     4. 认证通过后应答mqtt,注册会话{@link DeviceSessionManager#compute(String, Function)}.
 *     5. 监听mqtt消息推送,{@link MqttConnection#handleMessage()}
 *     6. 当收到消息时,调用对应设备使用的协议{@link ProtocolSupport#getMessageCodec(Transport)}进行解码{@link DeviceMessageCodec#decode(MessageDecodeContext)}
 * </pre>
 *
 * @author zhouhao
 * @see MqttServer
 * @see ProtocolSupport
 * @since 1.0
 */
@Slf4j
class MqttServerDeviceGateway extends AbstractDeviceGateway {
    static AttributeKey<String> clientId = AttributeKey.stringKey("clientId");

    static AttributeKey<String> username = AttributeKey.stringKey("username");

    static AttributeKey<String> password = AttributeKey.stringKey("password");


    //设备注册中心
    private final DeviceRegistry registry;

    //设备会话管理器
    private final DeviceSessionManager sessionManager;

    //Mqtt 服务
    @Getter
    private final MqttServer mqttServer;

    //解码后的设备消息处理器
    private final DecodedClientMessageHandler messageHandler;

    //连接计数器
    private final LongAdder counter = new LongAdder();

    //自定义的认证协议,在设备网关里配置自定义的认证协议来进行统一的设备认证处理
    //场景: 默认情况下时使用mqtt的clientId作为设备ID来进行设备与连接的绑定的,如果clientId的规则比较复杂
    //或者需要使用其他的clientId规则，则可以指定自定义的认证协议来进行认证.
    //指定了自定义协议的局限是: 所有使用同一个mqtt服务接入的设备，认证规则都必须一致才行.
    private final Mono<ProtocolSupport> supportMono;

    //注销监听器
    private Disposable disposable;

    //设备网关消息处理工具类
    private final DeviceGatewayHelper helper;

    public MqttServerDeviceGateway(String id,
                                   DeviceRegistry registry,
                                   DeviceSessionManager sessionManager,
                                   MqttServer mqttServer,
                                   DecodedClientMessageHandler messageHandler,
                                   Mono<ProtocolSupport> customProtocol) {
        super(id);
        this.registry = registry;
        this.sessionManager = sessionManager;
        this.mqttServer = mqttServer;
        this.messageHandler = messageHandler;
        this.supportMono = customProtocol;
        this.helper = new DeviceGatewayHelper(registry, sessionManager, messageHandler);
    }

    /**
     * 启动连接处理流程。
     * 该方法首先检查当前是否已有正在使用的disposable资源，如果有，则先停止并清理该资源。
     * 接着，创建一个新的MQTT连接监听器，用于处理设备的连接请求。
     * 如果在处理过程中发现服务未启动，将拒绝连接并记录相关信息。
     * 对于成功的连接，会经过认证和处理流程，最后订阅处理结果。
     */
    private void doStart() {
        // 清理已存在的资源
        if (disposable != null) {
            disposable.dispose();
        }
        // 设置新的连接监听
        disposable = mqttServer
            .handleConnection("device-gateway")
            // 过滤连接请求，判断服务是否已启动
            .filter(conn -> {
                if (!isStarted()) {
                    // 服务未启动，拒绝连接
                    conn.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
                    monitor.rejected();
                }
                return true;
            })
            // 处理MQTT连接请求的各个步骤
            .flatMap(connection -> this
                         .handleConnection(connection)
                         .flatMap(tuple3 -> handleAuthResponse(tuple3.getT1(), tuple3.getT2(), tuple3.getT3()))
                         .flatMap(tp -> handleAcceptedMqttConnection(tp.getT1(), tp.getT2(), tp.getT3()))
                         // 错误处理，记录错误信息并返回空Mono
                         .onErrorResume(err -> {
                             log.error(err.getMessage(), err);
                             return Mono.empty();
                         }),
                     Integer.MAX_VALUE)
            .subscribe();
    }


    /**
     * 处理MQTT连接并进行认证。
     * 此函数首先尝试使用自定义协议进行认证，如果未指定自定义协议，则使用clientId对应的设备进行认证。
     * 若认证失败或未进行认证，则拒绝连接。
     *
     * @param connection MQTT连接对象，包含客户端ID、认证信息等。
     * @return 返回一个包含设备操作符、认证响应和MQTT连接的Tuple3对象，或在认证失败时拒绝连接。
     */
    private Mono<Tuple3<DeviceOperator, AuthenticationResponse, MqttConnection>> handleConnection(MqttConnection connection) {

        // 尝试使用自定义协议或设备ID进行认证
        return Mono
            .justOrEmpty(connection.getAuth())
            .flatMap(auth -> {
                // 创建认证请求
                MqttAuthenticationRequest request = new MqttAuthenticationRequest(connection.getClientId(),
                                                                                  auth.getUsername(),
                                                                                  auth.getPassword(),
                                                                                  getTransport());
                return supportMono
                    // 尝试使用自定义协议进行认证
                    .map(support -> support.authenticate(request, registry))
                    // 若无自定义协议，则使用设备ID进行认证
                    .defaultIfEmpty(Mono.defer(() -> registry
                        .getDevice(connection.getClientId())
                        .flatMap(device -> device.authenticate(request))))
                    .flatMap(Function.identity())
                    // 若认证结果为空，则拒绝连接，防止安全问题
                    .switchIfEmpty(Mono.fromRunnable(() -> connection.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD)));
            })
            .flatMap(resp -> {
                // 处理认证响应，确定设备ID
                String deviceId = StringUtils.isEmpty(resp.getDeviceId()) ? connection.getClientId() : resp.getDeviceId();
                // 根据认证返回的设备ID获取设备操作符
                return registry
                    .getDevice(deviceId)
                    .map(operator -> Tuples.of(operator, resp, connection))
                    // 设备不存在则拒绝连接
                    .switchIfEmpty(Mono.fromRunnable(() -> connection.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED)))
                    ;
            })
            // 创建跟踪器，记录认证过程
            .as(MonoTracer
                    .create(SpanName.auth(connection.getClientId()),
                            (span, tp3) -> {
                                // 认证失败设置状态
                                AuthenticationResponse response = tp3.getT2();
                                if (!response.isSuccess()) {
                                    span.setStatus(StatusCode.ERROR, response.getMessage());
                                }
                            },
                            (span, hasValue) -> {
                                // 记录连接信息和设备不存在的情况
                                if (!hasValue) {
                                    span.setStatus(StatusCode.ERROR, "device not exists");
                                }
                                InetSocketAddress address = connection.getClientAddress();
                                if (address != null) {
                                    span.setAttribute(SpanKey.address, address.toString());
                                }
                                span.setAttribute(clientId, connection.getClientId());
                            }))
            // 认证错误处理，拒绝连接
            .onErrorResume((err) -> Mono.fromRunnable(() -> {
                // 记录日志、监控信息并拒绝连接
                log.error("MQTT连接认证[{}]失败", connection.getClientId(), err);
                monitor.rejected();
                connection.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
            }))
            ;
    }


    /**
     * 处理设备认证响应。
     *
     * @param device 设备操作者信息
     * @param resp 认证响应结果
     * @param connection MQTT连接对象
     * @return 返回包含MQTT连接、设备操作者和连接会话的Tuple3，如果认证失败或出现异常，则不返回任何内容
     */
    private Mono<Tuple3<MqttConnection, DeviceOperator, MqttConnectionSession>> handleAuthResponse(DeviceOperator device,
                                                                                                   AuthenticationResponse resp,
                                                                                                   MqttConnection connection) {
        return Mono
            .defer(() -> {
                String deviceId = device.getDeviceId();
                // 认证成功时的处理逻辑
                if (resp.isSuccess()) {
                    // 监听MQTT连接的关闭事件，并进行相应的资源清理和监控信息更新
                    connection.onClose(conn -> {
                        // 更新连接计数器和监控信息
                        counter.decrement();
                        monitor.disconnected();
                        monitor.totalConnection(counter.sum());

                        // 移除已连接设备的会话，如果该会话是当前连接的会话
                        sessionManager
                            .getSession(deviceId, false)
                            .flatMap(_tmp -> {
                                if (_tmp != null && _tmp.isWrapFrom(MqttConnectionSession.class) && !(_tmp instanceof KeepOnlineSession)) {
                                    MqttConnectionSession connectionSession = _tmp.unwrap(MqttConnectionSession.class);
                                    if (connectionSession.getConnection() == conn) {
                                        return sessionManager.remove(deviceId, true);
                                    }
                                }
                                return Mono.empty();
                            })
                            .subscribe();
                    });

                    // 更新连接计数器和创建新的会话
                    counter.increment();
                    return sessionManager
                        .compute(deviceId, old -> {
                            // 创建新的MQTT连接会话或根据旧会话更新
                            MqttConnectionSession newSession = new MqttConnectionSession(deviceId, device, getTransport(), connection, monitor);
                            return old
                                .<DeviceSession>map(session -> {
                                    if (session instanceof KeepOnlineSession) {
                                        // 如果是保持在线的会话，则继续保持
                                        return new KeepOnlineSession(newSession, session.getKeepAliveTimeout());
                                    }
                                    return newSession;
                                })
                                .defaultIfEmpty(newSession);
                        })
                        .mapNotNull(session -> {
                            // 尝试接受连接，如果成功则构建Tuple3返回
                            try {
                                return Tuples.of(connection.accept(), device, session.unwrap(MqttConnectionSession.class));
                            } catch (IllegalStateException ignore) {
                                // 忽略连接已被中断的异常
                                return null;
                            }
                        })
                        .doOnNext(o -> {
                            // 更新监控信息
                            monitor.connected();
                            monitor.totalConnection(counter.sum());
                        })
                        // 如果会话注册失败，则拒绝连接
                        .switchIfEmpty(Mono.fromRunnable(() -> connection.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED)));
                } else {
                    // 认证失败时的处理逻辑
                    // 根据认证失败的原因返回相应的拒绝码
                    connection.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
                    monitor.rejected();
                    log.warn("MQTT客户端认证[{}]失败:{}", deviceId, resp.getMessage());
                }
                return Mono.empty();
            })
            .onErrorResume(error -> Mono.fromRunnable(() -> {
                // 处理处理过程中的异常，记录日志并拒绝连接
                log.error(error.getMessage(), error);
                monitor.rejected();
                connection.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
            }))
            ;
    }


    /**
     * 处理已经建立连接的MQTT连接。
     * 此函数主要负责接收并处理MQTT连接上的消息，包括正常消息和遗言消息。
     *
     * @param connection MQTT连接对象，用于消息的发送和关闭。
     * @param operator 设备操作器，用于处理解码后的消息。
     * @param session MQTT连接会话，包含连接相关的会话信息。
     * @return Mono<Void>，表示异步操作完成。
     */
    private Mono<Void> handleAcceptedMqttConnection(MqttConnection connection,
                                                    DeviceOperator operator,
                                                    MqttConnectionSession session) {
        return Flux
            // 使用Flux的usingWhen操作符管理资源，这里主要是处理MQTT消息和连接的关闭。
            .usingWhen(Mono.just(connection),
                       MqttConnection::handleMessage,
                       MqttConnection::close)
            // 过滤掉在网关暂停或已停止时收到的消息，不进行处理。
            .filter(pb -> isStarted())
            // 在并行线程中发布处理消息，以提高处理效率。
            .publishOn(Schedulers.parallel())
            // 解码收到的MQTT消息，并处理之。
            .concatMap(publishing -> this
                .decodeAndHandleMessage(operator, session, publishing, connection)
                .as(MonoTracer
                        .create(SpanName.upstream(connection.getClientId()),
                                (span) -> span.setAttribute(SpanKey.message, publishing.print())))
            )
            // 合并处理遗言消息，如果存在的话。
            .mergeWith(
                Mono.justOrEmpty(connection.getWillMessage())
                    // 解码并处理遗言消息。
                    .flatMap(mqttMessage -> this.decodeAndHandleMessage(operator, session, mqttMessage, connection))
            )
            // 所有消息处理完成后，返回空的Mono表示操作完成。
            .then();
    }


    /**
     * 解码接收到的消息并进行处理。
     *
     * @param operator 设备操作者，负责标识操作设备的身份。
     * @param session MQTT连接会话信息。
     * @param message 接收到的MQTT消息。
     * @param connection MQTT连接实例。
     * @return 返回一个Mono<Void>，表示异步处理完成的信号。
     */
    private Mono<Void> decodeAndHandleMessage(DeviceOperator operator,
                                              MqttConnectionSession session,
                                              MqttMessage message,
                                              MqttConnection connection) {
        monitor.receivedMessage(); // 监控收到的消息

        // 根据设备操作者获取协议，然后解码消息
        return operator
            .getProtocol()
            .flatMap(protocol -> protocol.getMessageCodec(getTransport()))
            .flatMapMany(codec -> codec.decode(FromDeviceMessageContext.of(
                session, message, registry, msg -> handleMessage(operator, msg, connection).then())))
            .cast(DeviceMessage.class) // 将解码后的消息转换为DeviceMessage类型
            .concatMap(msg -> {
                // 如果消息中没有设备ID，则使用连接中的设备ID进行填充
                if (!StringUtils.hasText(msg.getDeviceId())) {
                    msg.thingId(DeviceThingType.device, operator.getDeviceId());
                }
                return this.handleMessage(operator, msg, connection);
            })
            .doOnComplete(() -> {
                // 消息处理完成后，对MQTT发布消息进行确认
                if (message instanceof MqttPublishing) {
                    ((MqttPublishing) message).acknowledge();
                }
            })
            .as(FluxTracer // 对消息处理添加跟踪
                    .create(SpanName.decode(operator.getDeviceId()),
                            (span, msg) -> span.setAttribute(SpanKey.message, toJsonString(msg.toJson()))))
            // 错误处理，发生错误时忽略并继续处理后续消息
            .onErrorResume((err) -> {
                log.error("handle mqtt message [{}] error:{}", operator.getDeviceId(), message, err);
                return Mono.empty();
            })
            .then()
            ;
    }


    /**
     * 将对象转换为JSON字符串。
     *
     * @param data 需要转换为JSON字符串的对象。
     * @return 返回对象的JSON字符串表示。
     * @throws Exception 如果转换过程中发生错误，则抛出异常。
     */
    @SneakyThrows
    private String toJsonString(Object data){
        // 使用ObjectMappers的JSON_MAPPER实例将对象序列化为JSON字符串
        return ObjectMappers.JSON_MAPPER.writeValueAsString(data);
    }

    /**
     * 处理设备消息的函数。
     *
     * @param mainDevice 主设备对象，代表与系统交互的设备。
     * @param message 设备发送的消息。
     * @param connection 设备的MQTT连接对象，用于与设备保持通信。
     * @return 返回一个包含设备消息的Mono对象，表示异步处理的结果。
     */
    private Mono<DeviceMessage> handleMessage(DeviceOperator mainDevice,
                                              DeviceMessage message,
                                              MqttConnection connection) {
        // 当连接已经断开时，直接处理消息，不再考虑会话状态，以避免设备多次上线的问题。
        if (!connection.isAlive()) {
            return messageHandler
                .handleMessage(mainDevice, message)
                .thenReturn(message);
        }
        // 如果连接仍然活跃，则统一处理解码后的设备消息，包括创建会话等操作。
        return helper.handleDeviceMessage(message,
                                          device -> new MqttConnectionSession(device.getDeviceId(),
                                                                              device,
                                                                              getTransport(),
                                                                              connection,
                                                                              monitor),
                                          session -> {

                                          },
                                          () -> {
                                              // 当无法从MQTT消息中获取设备信息时，记录警告日志。
                                              log.warn("无法从MQTT[{}]消息中获取设备信息:{}", connection.getClientId(), message);
                                          })
                     .thenReturn(message);
    }


    /**
     * 在组件启动时执行的逻辑。此方法会触发组件的启动流程。
     *
     * @return {@link Mono<Void>} 表示异步操作完成的信号，不返回任何数据。
     */
    @Override
    protected Mono<Void> doStartup() {
        // 触发组件的开始操作
        doStart();
        // 返回一个空的Mono对象，表示异步操作已完成
        return Mono.empty();
    }


    /**
     * 执行组件的关闭操作。
     * 该方法会在组件关闭时被调用，用于资源的释放等清理工作。
     *
     * @return {@link Mono<Void>} 表示异步关闭操作完成的信号。当关闭操作完成时，返回一个空的Mono。
     */
    @Override
    protected Mono<Void> doShutdown() {
        // 如果disposable不为空且未被处理，则进行资源的释放
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
        // 返回一个空的Mono，表示关闭操作已完成
        return Mono.empty();
    }


    /**
     * 获取传输协议类型。
     * 这个方法不接受任何参数，它用于返回系统默认的传输协议，即MQTT协议。
     *
     * @return Transport 返回传输协议的枚举类型。这里固定返回MQTT协议。
     */
    public Transport getTransport() {
        return DefaultTransport.MQTT;
    }

}
