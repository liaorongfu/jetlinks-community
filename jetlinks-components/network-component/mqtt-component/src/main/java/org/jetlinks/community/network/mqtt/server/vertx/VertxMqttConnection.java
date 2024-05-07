package org.jetlinks.community.network.mqtt.server.vertx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SocketAddress;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.network.mqtt.server.MqttConnection;
import org.jetlinks.community.network.mqtt.server.MqttPublishing;
import org.jetlinks.community.network.mqtt.server.MqttSubscription;
import org.jetlinks.community.network.mqtt.server.MqttUnSubscription;
import org.jetlinks.core.message.codec.EncodedMessage;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.core.message.codec.SimpleMqttMessage;
import org.jetlinks.core.server.mqtt.MqttAuth;
import org.jetlinks.core.utils.Reactors;
import reactor.core.publisher.*;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
class VertxMqttConnection implements MqttConnection {

    private final MqttEndpoint endpoint;
    private long keepAliveTimeoutMs;
    @Getter
    private long lastPingTime = System.currentTimeMillis();
    private volatile boolean closed = false, accepted = false, autoAckSub = true, autoAckUnSub = true, autoAckMsg = false;
    private int messageIdCounter;

    private static final MqttAuth emptyAuth = new MqttAuth() {
        @Override
        public String getUsername() {
            return "";
        }

        @Override
        public String getPassword() {
            return "";
        }
    };
    private final Sinks.Many<MqttPublishing> messageProcessor = Reactors.createMany(Integer.MAX_VALUE, false);
    private final Sinks.Many<MqttSubscription> subscription = Reactors.createMany(Integer.MAX_VALUE, false);
    private final Sinks.Many<MqttUnSubscription> unsubscription = Reactors.createMany(Integer.MAX_VALUE, false);


    public VertxMqttConnection(MqttEndpoint endpoint) {
        this.endpoint = endpoint;
        this.keepAliveTimeoutMs = (endpoint.keepAliveTimeSeconds() + 10) * 1000L;
    }

    private final Consumer<MqttConnection> defaultListener = mqttConnection -> {
        VertxMqttConnection.log.debug("mqtt client [{}] disconnected", getClientId());
        subscription.tryEmitComplete();
        unsubscription.tryEmitComplete();
        messageProcessor.tryEmitComplete();

    };

    private Consumer<MqttConnection> disconnectConsumer = defaultListener;

    /**
     * 获取保活超时时间。
     * 这个方法返回设置的保活（Keep-Alive）超时时间，单位为毫秒。
     * 保活机制用于在客户端和服务器之间保持连接的活性，避免因为网络闲置引起的连接断开。
     *
     * @return Duration 返回保活超时时间，单位为毫秒。
     */
    @Override
    public Duration getKeepAliveTimeout() {
        // 将保活超时时间（毫秒）转换为Duration对象并返回
        return Duration.ofMillis(keepAliveTimeoutMs);
    }


    /**
     * 注册一个在连接关闭时被调用的消费者监听器。
     * 该方法允许将一个新的消费者监听器添加到一个调用链中，当MQTT连接关闭时，所有的监听器将按照注册的顺序被依次调用。
     *
     * @param listener 要注册的MQTT连接关闭时的消费者监听器。该消费者接收一个MqttConnection类型的参数。
     */
    @Override
    public void onClose(Consumer<MqttConnection> listener) {
        // 将新的监听器添加到现有的监听器链中，确保在连接关闭时按顺序调用所有监听器
        disconnectConsumer = disconnectConsumer.andThen(listener);
    }


    /**
     * 获取认证信息的函数。
     * 这个函数会根据当前端点的认证信息决定返回哪种类型的认证对象。
     * 如果端点没有认证信息，则返回一个空的认证对象；否则，返回一个新的VertxMqttAuth认证对象。
     *
     * @return Optional<MqttAuth> 如果端点有认证信息，则返回包含一个VertxMqttAuth对象的Optional；
     *         如果没有，则返回包含一个空Auth对象的Optional。
     */
    @Override
    public Optional<MqttAuth> getAuth() {
        // 根据endpoint的认证信息决定返回的Optional内容
        return endpoint.auth() == null ? Optional.of(emptyAuth) : Optional.of(new VertxMqttAuth());
    }

    /**
     * 拒绝连接请求。
     * 该方法会根据提供的MqttConnectReturnCode拒绝一个MQTT连接。如果当前对象已经关闭，则直接返回。
     * 尝试调用endpoint的reject方法来执行实际的拒绝操作，并在操作完成后调用complete方法进行后续处理。
     *
     * @param code 拒绝连接的返回码，表示拒绝的原因。
     */
    @Override
    public void reject(MqttConnectReturnCode code) {
        if (closed) { // 检查对象是否已关闭，若已关闭，则直接返回，不执行后续操作
            return;
        }
        try {
            endpoint.reject(code); // 尝试通过endpoint拒绝连接
        } catch (Throwable ignore) {
            // 忽略endpoint拒绝操作中可能出现的任何异常
        }
        try {
            complete(); // 完成拒绝操作后的后续处理
        } catch (Throwable ignore) {
            // 忽略complete方法中可能出现的任何异常
        }
    }

    /**
     * 获取MQTT遗嘱消息。
     * 此方法会检查是否存在遗嘱（will），如果存在，并且遗嘱消息字节不为空，则构建并返回一个包含遗嘱消息的Optional对象。
     * 如果不存在有效的遗嘱消息，则返回一个空的Optional。
     *
     * @return Optional<MqttMessage> 包含遗嘱消息的Optional，如果没有有效的遗嘱消息，则为一个空的Optional。
     */
    @Override
    public Optional<MqttMessage> getWillMessage() {
        // 从端点获取遗嘱信息，如果存在，进一步过滤检查遗嘱消息字节是否非空
        return Optional.ofNullable(endpoint.will())
                       .filter(will -> will.getWillMessageBytes() != null) // 确保遗嘱消息非空
                       .map(will -> SimpleMqttMessage
                           .builder() // 使用SimpleMqttMessage的构建器来创建一个新的MqttMessage
                           .will(true) // 标记为遗嘱消息
                           .payload(Unpooled.wrappedBuffer(will.getWillMessageBytes())) // 设置消息体
                           .topic(will.getWillTopic()) // 设置主题
                           .qosLevel(will.getWillQos()) // 设置QoS级别
                           .build()); // 构建并返回MqttMessage
    }


    /**
     * 尝试接受一个MQTT连接。如果连接尚未被接受，则尝试与MQTT端点建立连接，并初始化相关设置。
     *
     * @return MqttConnection 返回当前MQTT连接实例。如果连接已成功接受，则返回此实例以供进一步操作。
     */
    @Override
    public MqttConnection accept() {
        // 检查连接是否已经被接受，如果是，则直接返回当前实例
        if (accepted) {
            return this;
        }
        // 记录MQTT客户端连接日志
        log.debug("mqtt client [{}] connected", getClientId());
        accepted = true;
        try {
            // 检查端点是否已连接，如果没有则尝试建立连接
            if (!endpoint.isConnected()) {
                endpoint.accept();
            }
        } catch (Exception e) {
            // 在连接建立失败时，关闭连接并记录异常信息
            close().subscribe();
            log.warn(e.getMessage(), e);
            return this;
        }
        // 初始化连接设置
        init();
        return this;
    }


    /**
     * 维持连接活性的方法。
     * 该方法通过执行ping操作来保持连接的活性，防止连接被中断。
     * 无参数。
     * 无返回值。
     */
    @Override
    public void keepAlive() {
        ping(); // 执行ping操作以保持连接活性
    }


    /**
     * 执行网络ping操作，用于检测网络连接的可用性。
     * 该方法不接受参数，也不返回任何值。
     * 主要作用是更新最后一次ping操作的时间。
     */
    void ping() {
        // 更新最后一次ping操作的时间为当前时间
        lastPingTime = System.currentTimeMillis();
    }


    /**
     * 初始化函数，配置MQTT连接的各种处理器。
     * 此函数不接受参数，也不返回任何值。
     * 主要完成以下配置：
     * 1. 定义断开连接、关闭连接的处理逻辑。
     * 2. 定义异常处理逻辑，特别是针对消息过大的特殊处理。
     * 3. 定义PING响应逻辑，以及各种消息发布和订阅的处理逻辑。
     */
    void init() {
        this.endpoint
            // 配置断开连接处理器
            .disconnectHandler(ignore -> this.complete())
            // 配置关闭连接处理器
            .closeHandler(ignore -> this.complete())
            // 配置异常处理器，对特定异常进行日志记录
            .exceptionHandler(error -> {
                if (error instanceof DecoderException) {
                    if (error.getMessage().contains("too large message")) {
                        log.error("MQTT消息过大,请在网络组件中设置[最大消息长度].", error);
                        return;
                    }
                }
                log.error(error.getMessage(), error);
            })
            // 配置PING处理器
            .pingHandler(ignore -> {
                this.ping();
                if (!endpoint.isAutoKeepAlive()) {
                    endpoint.pong();
                }
            })
            // 配置消息发布处理器
            .publishHandler(msg -> {
                ping();
                VertxMqttPublishing publishing = new VertxMqttPublishing(msg, false);
                boolean hasDownstream = this.messageProcessor.currentSubscriberCount() > 0;
                if (autoAckMsg && hasDownstream) {
                    publishing.acknowledge();
                }
                if (hasDownstream) {
                    this.messageProcessor.emitNext(publishing, Reactors.emitFailureHandler());
                }
            })
            // 配置QoS 1级别的PUBACK消息处理器
            .publishAcknowledgeHandler(messageId -> {
                ping();
                log.debug("PUBACK mqtt[{}] message[{}]", getClientId(), messageId);
            })
            // 配置QoS 2级别的PUBREC消息处理器
            .publishReceivedHandler(messageId -> {
                ping();
                log.debug("PUBREC mqtt[{}] message[{}]", getClientId(), messageId);
                endpoint.publishRelease(messageId);
            })
            // 配置QoS 2级别的PUBREL消息处理器
            .publishReleaseHandler(messageId -> {
                ping();
                log.debug("PUBREL mqtt[{}] message[{}]", getClientId(), messageId);
                endpoint.publishComplete(messageId);
            })
            // 配置QoS 2级别的PUBCOMP消息处理器
            .publishCompletionHandler(messageId -> {
                ping();
                log.debug("PUBCOMP mqtt[{}] message[{}]", getClientId(), messageId);
            })
            // 配置订阅请求处理器
            .subscribeHandler(msg -> {
                ping();
                VertxMqttSubscription subscription = new VertxMqttSubscription(msg, false);
                boolean hasDownstream = this.subscription.currentSubscriberCount() > 0;
                if (autoAckSub || !hasDownstream) {
                    subscription.acknowledge();
                }
                if (hasDownstream) {
                    this.subscription.emitNext(subscription, Reactors.emitFailureHandler());
                }
            })
            // 配置取消订阅请求处理器
            .unsubscribeHandler(msg -> {
                ping();
                VertxMqttMqttUnSubscription unSubscription = new VertxMqttMqttUnSubscription(msg, false);
                boolean hasDownstream = this.unsubscription.currentSubscriberCount() > 0;
                if (autoAckUnSub || !hasDownstream) {
                    unSubscription.acknowledge();
                }
                if (hasDownstream) {
                    this.unsubscription.emitNext(unSubscription, Reactors.emitFailureHandler());
                }
            });
    }

    /**
     * 设置保持活动状态的超时时间。
     * 这个方法允许调用者指定一个持续时间，此持续时间过后，如果连接没有活动，将被认为是超时。
     *
     * @param duration 指定的持续时间，单位为毫秒。
     */
    @Override
    public void setKeepAliveTimeout(Duration duration) {
        // 将传入的持续时间转换为毫秒，并更新保持活动状态的超时时间
        keepAliveTimeoutMs = duration.toMillis();
    }


    private volatile InetSocketAddress clientAddress;

    /**
     * 获取客户端的地址信息。
     * 此方法会尝试从连接的端点(endpoint)中提取远程地址，并将其转换为InetSocketAddress形式。
     * 如果端点地址不存在或无法获取，则返回null。
     *
     * @return InetSocketAddress 客户端的地址信息，如果无法获取则返回null。
     */
    @Override
    public InetSocketAddress getClientAddress() {
        try {
            // 如果clientAddress尚未初始化且endpoint不为空，则尝试获取并设置clientAddress
            if (clientAddress == null && endpoint != null) {
                SocketAddress address = endpoint.remoteAddress(); // 尝试获取端点的远程地址
                if (address != null) {
                    // 如果成功获取到地址，则将其转换为InetSocketAddress形式并保存
                    clientAddress = new InetSocketAddress(address.host(), address.port());
                }
            }
        } catch (Throwable ignore) {
            // 捕获并忽略所有异常，防止因异常导致方法中断执行
        }
        // 返回clientAddress，即使在获取过程中遇到异常也保证有返回值
        return clientAddress;
    }


    /**
     * 获取客户端标识符。
     * 该方法重写了获取客户端ID的功能，直接从endpoint对象中提取clientIdentifier并返回。
     *
     * @return 返回客户端的唯一标识符，类型为String。
     */
    @Override
    public String getClientId() {
        // 从endpoint中获取客户端标识符并返回
        return endpoint.clientIdentifier();
    }


    /**
     * 处理消息的方法。
     * 该方法重写了父类或接口中的同名方法，旨在处理消息并以Flux形式返回处理结果。
     *
     * @return Flux<MqttPublishing> 返回一个Flux流，包含处理后的MqttPublishing对象序列。
     */
    @Override
    public Flux<MqttPublishing> handleMessage() {
        // 将消息处理器的结果转换为Flux流并返回
        return messageProcessor.asFlux();
    }

    /**
     * 发布一个MQTT消息到指定的主题。
     *
     * @param message 包含要发布消息的主题和消息体的MqttMessage对象。
     *                如果消息ID小于等于0，则会自动生成一个消息ID。
     * @return 返回一个Mono<Void>对象，表示异步操作的结果。如果操作成功，Mono<Void>将会完成；
     *         如果操作失败，将会通过错误通道传递异常。
     */
    @Override
    public Mono<Void> publish(MqttMessage message) {
        ping(); // 发送一个ping请求以确保连接活性

        // 生成或使用提供的消息ID
        int messageId = message.getMessageId() <= 0 ? nextMessageId() : message.getMessageId();

        return Mono
            .<Void>create(sink -> {
                ByteBuf buf = message.getPayload(); // 获取消息体
                Buffer buffer = Buffer.buffer(buf); // 将ByteBuf转换为Vert.x的Buffer

                // 发布消息到指定主题
                endpoint.publish(
                    message.getTopic(),
                    buffer,
                    MqttQoS.valueOf(message.getQosLevel()), // 设置质量等级
                    message.isDup(), // 设置是否重复消息
                    message.isRetain(), // 设置是否保留消息
                    messageId, // 设置消息ID
                    message.getProperties(), // 设置消息属性
                    result -> {
                        if (result.succeeded()) { // 操作成功
                            sink.success(); // 完成Mono
                        } else { // 操作失败
                            sink.error(result.cause()); // 通过错误通道传递异常
                        }
                        ReferenceCountUtil.safeRelease(buf); // 释放ByteBuf资源
                    }
                );
            });
    }


    /**
     * 处理订阅请求的函数。
     *
     * @param autoAck 是否自动确认订阅。如果为true，则自动确认订阅；如果为false，则需要手动确认订阅。
     * @return Flux<MqttSubscription> 返回一个MqttSubscription的Flux流，包含了所有的订阅信息。
     */
    @Override
    public Flux<MqttSubscription> handleSubscribe(boolean autoAck) {

        // 设置自动确认订阅的状态
        autoAckSub = autoAck;
        // 将订阅信息转换为Flux流返回
        return subscription.asFlux();
    }


    /**
     * 处理取消订阅的请求。
     *
     * @param autoAck 指示是否自动确认取消订阅操作。如果为true，则自动确认取消订阅；否则，需要手动确认。
     * @return 返回一个Flux流，包含取消订阅的操作信息。
     */
    @Override
    public Flux<MqttUnSubscription> handleUnSubscribe(boolean autoAck) {
        autoAckUnSub = autoAck; // 设置自动确认取消订阅的标志
        return unsubscription.asFlux(); // 将取消订阅的信息转换为Flux流返回
    }


    /**
     * 获取客户端的地址信息。
     * 该方法重写了address()方法，以提供具体的客户端地址。
     *
     * @return InetSocketAddress 返回客户端的地址和端口信息。
     */
    @Override
    public InetSocketAddress address() {
        // 获取并返回客户端的地址
        return getClientAddress();
    }

    /**
     * 发送消息的方法。
     * 如果传入的消息是MqttMessage类型的实例，则会通过publish方法发布该消息。
     * 如果传入的消息不是MqttMessage类型的实例，则不会进行任何操作，直接返回一个空的Mono<Void>。
     *
     * @param message 要发送的消息，类型为EncodedMessage。这个参数可以是任何实现了EncodedMessage接口的消息对象。
     * @return 返回一个Mono<Void>。如果消息是MqttMessage类型，则在消息发布后返回；否则直接返回一个空的Mono<Void>。
     */
    @Override
    public Mono<Void> sendMessage(EncodedMessage message) {
        if (message instanceof MqttMessage) {
            // 如果消息是MqttMessage类型，进行消息发布
            return publish(((MqttMessage) message));
        }
        // 如果消息不是MqttMessage类型，直接返回空的Mono<Void>
        return Mono.empty();
    }

    /**
     * 接收消息并返回一个Flux流，该流包含了接收到的编码消息。
     *
     * @return Flux<EncodedMessage> 返回一个Flux流，该流不断发出接收到的编码消息。
     */
    @Override
    public Flux<EncodedMessage> receiveMessage() {
        // 处理消息并将其转换为EncodedMessage类型的Flux流
        return handleMessage()
            .cast(EncodedMessage.class);
    }

    /**
     * 断开连接的方法。
     * 此方法通过调用close()方法来请求关闭连接，并且订阅close()方法的执行结果。
     * 该方法没有参数。
     * 没有返回值，因为它的目的是执行操作，而不是返回结果。
     */
    @Override
    public void disconnect() {
        close().subscribe(); // 调用close方法并订阅其结果，以确保连接被正确且安全地关闭
    }

    /**
     * 检查当前连接是否仍然活跃。
     * 此方法通过检查端点是否连接以及是否在保持活跃的超时时间内发送了ping来确定连接是否仍然活跃。
     *
     * @return boolean - 如果连接仍然活跃，则返回true；否则返回false。
     */
    @Override
    public boolean isAlive() {
        // 检查端点是否连接，并且判断是否在keepAliveTimeoutMs规定的时间内发送了ping
        return endpoint.isConnected() && (keepAliveTimeoutMs < 0 || ((System.currentTimeMillis() - lastPingTime) < keepAliveTimeoutMs));
    }

    /**
     * 覆盖close方法，当连接已建立时关闭endpoint，否则完成操作。
     * 如果endpoint已经关闭，将立即返回一个空的Mono。
     *
     * @return Mono<Void> - 若endpoint已关闭或关闭操作成功完成时返回一个空的Mono。
     */
    @Override
    public Mono<Void> close() {
        // 检查endpoint是否已关闭，如果已关闭则直接返回空Mono
        if (closed) {
            return Mono.empty();
        }
        // 创建一个异步执行关闭操作的Mono
        return Mono.<Void>fromRunnable(() -> {
            try {
                // 如果endpoint已连接，尝试关闭它，否则完成操作
                if (endpoint.isConnected()) {
                    endpoint.close(); // 尝试关闭endpoint
                } else {
                    complete(); // 若endpoint未连接，完成操作
                }
            } catch (Throwable ignore) {
                // 捕获并忽略在关闭操作中抛出的任何异常
            }
        });

    }

    /**
     * 标记当前对象完成并执行断开连接的操作。
     * 该方法首先检查对象是否已经关闭，如果是，则直接返回；
     * 如果未关闭，则标记为已关闭，并调用提供的断开连接消费者接口，
     * 以此执行相关的断开连接逻辑。
     */
    private void complete() {
        // 检查对象是否已经关闭，如果是，则直接返回
        if (closed) {
            return;
        }
        closed = true; // 标记为已关闭
        disconnectConsumer.accept(this); // 执行断开连接操作
    }



    /**
     * 一个用于MQTT消息发布的Vertx实现类。
     */
    @AllArgsConstructor
    class VertxMqttPublishing implements MqttPublishing {

        /**
         * 将要发布的MQTT消息。
         */
        private final MqttPublishMessage message;

        /**
         * 消息是否已被确认。
         */
        private volatile boolean acknowledged;

        /**
         * 获取消息的主题。
         *
         * @return 返回消息的主题字符串。
         */
        @Nonnull
        @Override
        public String getTopic() {
            return message.topicName();
        }

        /**
         * 获取客户端ID。
         *
         * @return 返回客户端的ID。
         */
        @Override
        public String getClientId() {
            return VertxMqttConnection.this.getClientId();
        }

        /**
         * 获取消息的ID。
         *
         * @return 返回消息的ID。
         */
        @Override
        public int getMessageId() {
            return message.messageId();
        }

        /**
         * 判断此消息是否为遗嘱消息。
         *
         * @return 永远返回false，因为此类不处理遗嘱消息。
         */
        @Override
        public boolean isWill() {
            return false;
        }

        /**
         * 获取消息的质量等级（QoS）。
         *
         * @return 返回消息的质量等级。
         */
        @Override
        public int getQosLevel() {
            return message.qosLevel().value();
        }

        /**
         * 判断消息是否设置为重复标志。
         *
         * @return 如果消息设置为重复，则返回true，否则返回false。
         */
        @Override
        public boolean isDup() {
            return message.isDup();
        }

        /**
         * 判断消息是否设置为保留标志。
         *
         * @return 如果消息设置为保留，则返回true，否则返回false。
         */
        @Override
        public boolean isRetain() {
            return message.isRetain();
        }

        /**
         * 获取消息的负载数据。
         *
         * @return 返回消息的负载数据的ByteBuf实例。
         */
        @Nonnull
        @Override
        public ByteBuf getPayload() {
            return message.payload().getByteBuf();
        }

        /**
         * 生成并返回此消息的字符串表示。
         *
         * @return 返回此消息的字符串表示。
         */
        @Override
        public String toString() {
            return print();
        }

        /**
         * 获取消息的属性。
         *
         * @return 返回此消息的MQTT属性。
         */
        @Override
        public MqttProperties getProperties() {
            return message.properties();
        }

        /**
         * 获取代表此发布消息的MQTT消息实例。
         *
         * @return 返回此实例本身，因为自身即为一个MqttMessage。
         */
        @Override
        public MqttMessage getMessage() {
            return this;
        }

        /**
         * 确认消息的发布。
         * 根据消息的QoS等级，发送相应的确认消息给服务器。
         */
        @Override
        public void acknowledge() {
            if (acknowledged) {
                return;
            }
            acknowledged = true;
            // 根据QoS等级，发送不同的确认消息
            if (message.qosLevel() == MqttQoS.AT_LEAST_ONCE) {
                log.debug("PUBACK QoS1 mqtt[{}] message[{}]", getClientId(), message.messageId());
                endpoint.publishAcknowledge(message.messageId());
            } else if (message.qosLevel() == MqttQoS.EXACTLY_ONCE) {
                log.debug("PUBREC QoS2 mqtt[{}] message[{}]", getClientId(), message.messageId());
                endpoint.publishReceived(message.messageId());
            }
        }
    }

    /**
     * 一个用于Vertx Mqtt的订阅实现类，用于管理MQTT订阅消息及其确认。
     */
    @AllArgsConstructor
    class VertxMqttSubscription implements MqttSubscription {

        // 订阅消息，包含订阅的主题和QoS等级等信息。
        private final MqttSubscribeMessage message;

        // 订阅是否已被确认的标志。
        private volatile boolean acknowledged;

        /**
         * 获取订阅消息。
         *
         * @return 返回此订阅相关的MqttSubscribeMessage对象。
         */
        @Override
        public MqttSubscribeMessage getMessage() {
            return message;
        }

        /**
         * 确认订阅。
         * 这个方法是同步的，以确保订阅只被确认一次。
         * 当确认发生时，会将acknowledged标志设置为true，并向endpoint发送订阅确认。
         */
        @Override
        public synchronized void acknowledge() {
            if (acknowledged) {
                return; // 如果已经确认过，则直接返回，避免重复确认。
            }
            acknowledged = true; // 标记为已确认。
            // 向endpoint发送订阅确认，包含消息ID和订阅的主题及QoS列表。
            endpoint.subscribeAcknowledge(message.messageId(), message
                .topicSubscriptions()
                .stream()
                .map(MqttTopicSubscription::qualityOfService)
                .collect(Collectors.toList()));
        }
    }

    /**
     * 一个用于处理MQTT取消订阅的类，实现了MqttUnsubscription接口。
     * 使用@AllArgsConstructor注解使得它可以使用所有参数的构造函数。
     */
    @AllArgsConstructor
    class VertxMqttMqttUnSubscription implements MqttUnSubscription {

        // MQTT取消订阅的消息。
        private final MqttUnsubscribeMessage message;

        // 标记取消订阅是否已被确认。
        private volatile boolean acknowledged;

        /**
         * 获取取消订阅的消息。
         *
         * @return 返回取消订阅的消息对象。
         */
        @Override
        public MqttUnsubscribeMessage getMessage() {
            return message;
        }

        /**
         * 确认取消订阅操作。
         * 这个方法是同步的，以确保当多个线程尝试确认同一个取消订阅操作时，操作是安全的。
         * 只有在取消订阅未被确认的情况下才会执行确认操作。
         */
        @Override
        public synchronized void acknowledge() {
            if (acknowledged) {
                return;
            }
            // 记录取消订阅的确认信息。
            log.info("acknowledge mqtt [{}] unsubscribe : {} ", getClientId(), message.topics());
            acknowledged = true;
            // 向端点发送取消订阅的确认。
            endpoint.unsubscribeAcknowledge(message.messageId());
        }
    }


    /**
     * VertxMqttAuth 类实现了 MqttAuth 接口，用于提供MQTT认证所需的用户名和密码。
     */
    class VertxMqttAuth implements MqttAuth {

        /**
         * 获取认证的用户名。
         * @return 返回从endpoint的认证信息中获取的用户名。
         */
        @Override
        public String getUsername() {
            return endpoint.auth().getUsername();
        }

        /**
         * 获取认证的密码。
         * @return 返回从endpoint的认证信息中获取的密码。
         */
        @Override
        public String getPassword() {
            return endpoint.auth().getPassword();
        }
    }

    /**
     * 生成并返回下一个消息ID。
     * 消息ID在一个范围内循环使用，当达到最大值65535时，重置为1。
     * 这个方法没有参数。
     *
     * @return 返回一个整型的的消息ID。
     */
    private int nextMessageId() {
        // 如果当前消息ID不为0，则自增1；如果为0，则重置为1，确保消息ID不为0
        this.messageIdCounter = ((this.messageIdCounter % 65535) != 0) ? this.messageIdCounter + 1 : 1;
        return this.messageIdCounter;
    }


    /**
     * 检查当前对象是否与给定的对象相等。
     * 该方法首先检查两个对象是否为同一个对象的引用。如果是，则返回true。
     * 如果不是，它会检查给定的对象是否为null以及是否与当前对象的类相同。
     * 如果这些条件之一不满足，则返回false。
     * 最后，它会比较两个对象的端点(endpoint)字段，使用Objects.equals方法进行比较。
     *
     * @param o 要与当前对象进行比较的对象。
     * @return 如果当前对象等于参数给出的对象，则返回true；否则返回false。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true; // 检查两个对象是否为同一实例
        if (o == null || getClass() != o.getClass()) return false; // 检查对象是否为null或不同类
        VertxMqttConnection that = (VertxMqttConnection) o;
        return Objects.equals(endpoint, that.endpoint); // 比较两个对象的端点字段
    }

    /**
     * 生成当前对象的哈希码。
     * 该方法重写了hashCode方法，以提供基于对象的端点(endpoint)的哈希码。
     *
     * @return int 返回基于端点的哈希码值。使用了Java Object类中的Objects.hash()方法来计算哈希码。
     */
    @Override
    public int hashCode() {
        return Objects.hash(endpoint);
    }
}
