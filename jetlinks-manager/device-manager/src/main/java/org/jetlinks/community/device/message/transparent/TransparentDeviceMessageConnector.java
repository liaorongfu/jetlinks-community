package org.jetlinks.community.device.message.transparent;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.crud.events.EntityCreatedEvent;
import org.hswebframework.web.crud.events.EntityDeletedEvent;
import org.hswebframework.web.crud.events.EntityModifyEvent;
import org.hswebframework.web.crud.events.EntitySavedEvent;
import org.hswebframework.web.exception.ValidationException;
import org.jctools.maps.NonBlockingHashMap;
import org.jetlinks.community.gateway.DeviceGatewayHelper;
import org.jetlinks.core.device.DeviceConfigKey;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.device.session.DeviceSessionManager;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import org.jetlinks.core.message.*;
import org.jetlinks.core.message.interceptor.DeviceMessageSenderInterceptor;
import org.jetlinks.community.OperationSource;
import org.jetlinks.community.device.entity.TransparentMessageCodecEntity;
import org.jetlinks.community.gateway.annotation.Subscribe;
import org.jetlinks.supports.server.DecodedClientMessageHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
/**
 * 透明消息编码解码器管理类。
 * 该类负责管理透明消息的编码和解码器，以及与之相关的各种操作，如存储和检索编码解码器。
 */
@Slf4j
@Component
public class TransparentDeviceMessageConnector implements CommandLineRunner, DeviceMessageSenderInterceptor {

    /**
     * 用于存储和操作透明消息编码解码器实体的反应式仓库。
     * 这个仓库允许异步地存储和检索编码解码器实体，以支持高并发和低延迟的需求。
     */
    private final ReactiveRepository<TransparentMessageCodecEntity, String> repository;

    /**
     * 处理解码后客户端消息的处理器。
     * 该处理器负责对接收到的客户端消息进行进一步的处理，比如路由到相应的服务或方法。
     */
    private final DecodedClientMessageHandler messageHandler;

    /**
     * 事件总线，用于发布和订阅事件。
     * 通过事件总线，本类可以向其他组件或服务发布事件，比如编码解码器的添加或更新事件。
     */
    private final EventBus eventBus;

    /**
     * 缓存编码解码器的映射。
     * 这个映射用于快速访问和检索编码解码器，以减少对仓库的访问次数，提高性能。
     */
    private final Map<CacheKey, TransparentMessageCodec> codecs = new NonBlockingHashMap<>();

    /**
     * 设备网关助手类，提供与设备网关相关的辅助功能。
     * 该助手类可能用于与设备进行通信，比如发送指令或接收设备状态更新。
     */
    private final DeviceGatewayHelper gatewayHelper;
    /**
     * 透明设备消息连接器，负责处理设备的透明消息通信。
     *
     * 该连接器通过注册和管理不同的消息编解码器，处理设备端的透明消息，
     * 并将其转发给相应的处理程序。它利用事件总线来发布某些事件，
     * 并依赖于设备会话管理器和设备注册表来管理设备的会话和注册信息。
     *
     * @param repository 用于存储和检索TransparentMessageCodecEntity的反应式仓库。
     * @param messageHandler 解码客户端消息的处理程序，用于处理设备发送的透明消息。
     * @param sessionManager 设备会话管理器，用于管理设备的会话状态。
     * @param registry 设备注册表，用于注册和查找设备。
     * @param eventBus 事件总线，用于发布和订阅事件。
     * @param providers 透明消息编解码器提供者对象的提供者，用于注册和获取编解码器。
     */
    public TransparentDeviceMessageConnector(@SuppressWarnings("all")
                                             ReactiveRepository<TransparentMessageCodecEntity, String> repository,
                                             DecodedClientMessageHandler messageHandler,
                                             DeviceSessionManager sessionManager,
                                             DeviceRegistry registry,
                                             EventBus eventBus,
                                             ObjectProvider<TransparentMessageCodecProvider> providers) {
        this.repository = repository;
        this.messageHandler = messageHandler;
        this.eventBus = eventBus;
        this.gatewayHelper = new DeviceGatewayHelper(registry, sessionManager, messageHandler);
        for (TransparentMessageCodecProvider provider : providers) {
            TransparentMessageCodecProviders.addProvider(provider);
        }
    }


    /**
     * 处理设备直接发送的消息。
     *
     * 此方法订阅了特定主题的事件，当有设备直接发送消息时，此方法将被调用。
     * 主题模式为 " "，其中双星号表示通配符，可以匹配任何产品ID和设备ID。
     *
     * @param message 接收到的直接设备消息。该消息包含了设备发送的数据以及一些额外的头部信息。
     * @return 返回一个Mono<Void>对象，表示操作的结果。如果操作成功完成，将返回一个空的Mono；如果操作未能完成，可能会返回一个错误的Mono。
     */
    @Subscribe("/device/*/*/message/direct")
    public Mono<Void> handleMessage(DirectDeviceMessage message) {
        // 从消息头中获取产品ID，如果不存在则使用默认值。
        String productId = message.getHeaderOrDefault(Headers.productId);
        // 从消息中获取设备ID。
        String deviceId = message.getDeviceId();
        // 根据产品ID和设备ID获取相应的编解码器，如果不存在则返回null。
        TransparentMessageCodec codec = getCodecOrNull(productId, deviceId);
        // 如果没有找到相应的编解码器，则直接返回空的Mono，表示不进行任何处理。
        if (null == codec) {
            return Mono.empty();
        }
        // 使用编解码器解码消息，然后进一步处理解码后的消息。
        // 这里的flatMap操作将解码操作的结果转换为一个Mono<Void>，表示处理消息的操作。
        return codec
            .decode(message)
            .flatMap(this::handleMessage)
            .then();
    }


    /**
     * 处理设备消息。
     *
     * 此方法用于接收和处理来自设备的各类消息。根据消息的类型，它可能需要忽略会话信息，
     * 或者通过特定的处理器来处理消息。
     *
     * @param msg 待处理的设备消息。此消息可以是任何类型的设备消息，包括子设备消息。
     * @return Mono<Void> 表示异步处理操作的完成。
     */
    private Mono<Void> handleMessage(DeviceMessage msg) {
        // 检查消息是否为子设备消息或子设备消息回复
        if (msg instanceof ChildDeviceMessage || msg instanceof ChildDeviceMessageReply) {
            // 为子设备消息添加忽略会话的头部
            msg.addHeader(Headers.ignoreSession, true);
            // 使用网关助手处理设备消息，并忽略设备操作，然后完成处理
            return gatewayHelper
                .handleDeviceMessage(
                    msg,
                    device -> null
                )
                .then();
        }

        // 对于非子设备消息，使用通用的消息处理器来处理消息，然后完成处理
        return messageHandler.handleMessage(null, msg).then();
    }


    /**
     * 根据产品ID和设备ID获取透明消息编码器。
     * 此方法尝试从缓存中获取编码器，如果第一次获取失败（可能因为设备ID为空），会尝试再次获取时忽略设备ID。
     * 这种方法的设计考虑了缓存策略中可能存在的对设备ID不敏感的场景。
     *
     * @param productId 产品ID，用于定位特定产品的编码器。
     * @param deviceId 设备ID，用于进一步定位特定设备的编码器，某些情况下可以忽略。
     * @return 编码器实例，如果缓存中不存在则返回null。
     */
    private TransparentMessageCodec getCodecOrNull(String productId, String deviceId) {
        // 创建缓存键，产品ID和设备ID均为缓存键的一部分。
        CacheKey cacheKey = new CacheKey(productId, deviceId);
        // 尝试根据完整的缓存键获取编码器。
        TransparentMessageCodec codec = codecs.get(cacheKey);
        // 如果第一次获取失败（codec为null），尝试忽略设备ID再次获取。
        if (codec == null) {
            cacheKey.setDeviceId(null);
            codec = codecs.get(cacheKey);
        }
        // 返回获取到的编码器，如果仍然未获取到则返回null。
        return codec;
    }


    /**
     * 在发送设备消息之前进行预处理。
     * 此方法尝试根据设备的配置和消息类型来编码消息。如果编码成功，它将添加一些额外的头信息。
     * 如果编码失败或无法找到相应的编码器，则返回原始消息。
     *
     * @param device 设备操作接口，用于获取设备的特定配置。
     * @param message 待发送的设备消息。
     * @return 编码后的设备消息，如果无法编码，则返回原始消息。
     */
    @Override
    public Mono<DeviceMessage> preSend(DeviceOperator device, DeviceMessage message) {
        // 从设备配置中获取产品ID
        return device
            .getSelfConfig(DeviceConfigKey.productId)
            // 根据产品ID和设备ID获取编码器，忽略空值
            .mapNotNull(productId -> getCodecOrNull(productId, device.getDeviceId()))
            // 使用获取到的编码器对消息进行编码
            .<DeviceMessage>flatMap(codec -> codec
                .encode(message)
                .doOnNext(msg -> {
                    // 添加编码方式的头信息
                    msg.addHeader("encodeBy", message.getMessageType().name());
                    // 设置消息为异步发送
                    //所有透传消息都设置为异步
                    msg.addHeader(Headers.async, true);
                    // 注释掉的代码表示原本可能有但当前不需要的操作
                    // msg.addHeader(Headers.sendAndForget, true);
                })
            )
            // 如果编码失败或没有找到编码器，则返回原始消息
            .defaultIfEmpty(message);
    }



    /**
     * 加载透明编码插件。
     *
     * 此方法用于根据传入的 TransparentMessageCodecEntity 实例加载相应的透明编码插件。
     * 它首先根据实体中的产品ID和设备ID生成一个缓存键，然后尝试根据实体中的提供者名称获取编码器提供者。
     * 如果提供者不存在，则抛出 ValidationException 异常。
     * 获取提供者后，使用提供的配置创建编码器，并将编码器存储在 codecs 缓存中。
     * 如果创建编码器的结果为空，则从缓存中移除对应的编码器。
     *
     * @param entity 包含产品ID、设备ID、提供者名称和配置信息的透明编码实体。
     * @return Mono<Void> 表示异步操作完成的 Mono 实例。
     */
    @Subscribe(value = "/_sys/transparent-codec/load", features = Subscription.Feature.broker)
    public Mono<Void> doLoadCodec(TransparentMessageCodecEntity entity) {
        // 根据产品ID和设备ID生成缓存键
        CacheKey key = new CacheKey(entity.getProductId(), entity.getDeviceId());
        // 尝试获取编码器提供者，如果不存在则抛出异常
        TransparentMessageCodecProvider provider = TransparentMessageCodecProviders
            .getProvider(entity.getProvider())
            .orElseThrow(() -> new ValidationException("codec", "error.unsupported_codec", entity.getProvider()));
        // 创建编码器，并在创建后将其存储在缓存中
        // 如果创建结果为空，则从缓存中移除对应的编码器
        return provider
            .createCodec(entity.getConfiguration())
            .doOnNext(codec -> codecs.put(key, codec))
            .contextWrite(OperationSource.ofContext(entity.getId(), null, entity))
            .switchIfEmpty(Mono.fromRunnable(() -> codecs.remove(key)))
            .then();
    }


    /**
     * 当透明编码实体被移除时处理订阅事件。
     *
     * 该方法订阅了一个特定的事件主题，当系统中某个透明编码实体被移除时，此方法将被调用。
     * 它的主要作用是根据传入的透明编码实体信息，从编码器列表中移除相应的编码器。
     *
     * @param entity 透明编码实体，包含要移除的编码器的相关信息，如产品ID和设备ID。
     * @return 返回一个空的Mono对象，表示操作完成，不返回任何结果。
     */
    @Subscribe(value = "/_sys/transparent-codec/removed", features = Subscription.Feature.broker)
    public Mono<Void> doRemoveCodec(TransparentMessageCodecEntity entity) {
        // 根据产品ID和设备ID创建缓存键
        CacheKey key = new CacheKey(entity.getProductId(), entity.getDeviceId());
        // 从编码器列表中移除对应的编码器
        codecs.remove(key);
        // 返回一个空的Mono对象，表示操作已完成
        return Mono.empty();
    }

    /**
     * 处理实体创建事件。
     * 当 TransparentMessageCodecEntity 类型的实体被创建时，此方法将被调用。
     * 它的目的是异步加载实体中定义的编解码器。
     *
     * @param event 创建事件的对象，包含新创建的实体。
     */
    @EventListener
    public void handleEntityEvent(EntityCreatedEvent<TransparentMessageCodecEntity> event) {
        // 使用异步方式处理事件，以提高系统响应性和并发处理能力
        event.async(
            // 将实体转换为Flux流，以便逐个处理实体中的编解码器
            Flux.fromIterable(event.getEntity())
                // 对每个编解码器进行加载操作
                .flatMap(this::loadCodec)
        );
    }


    /**
     * 处理实体保存事件。
     * 当TransparentMessageCodecEntity类型的实体被保存时，此方法会被调用。
     * 它的目的是在实体保存后异步加载与实体相关的编解码器。
     *
     * @param event 保存事件的实例，包含待处理的实体。
     */
    @EventListener
    public void handleEntityEvent(EntitySavedEvent<TransparentMessageCodecEntity> event) {
        // 使用Reactor的Flux API将实体集合转换为异步流，并对每个实体加载编解码器
        event.async(
            Flux.fromIterable(event.getEntity())
                .flatMap(this::loadCodec)
        );
    }


    /**
     * 处理实体修改事件。
     * 当TransparentMessageCodecEntity实体被修改时，此方法被调用以异步加载修改后的实体所对应的编解码器。
     * 使用事件监听器注解@EventListener，使得该方法能够响应特定类型的事件。
     *
     * @param event 实体修改事件对象，包含修改前和修改后的实体信息。
     */
    @EventListener
    public void handleEntityEvent(EntityModifyEvent<TransparentMessageCodecEntity> event) {
        // 异步处理事件，以提高系统响应性和并发处理能力。
        event.async(
            // 从事件中获取修改后的实体集合，并转换为Flux流，以便进行反应式处理。
            Flux.fromIterable(event.getAfter())
                // 对每个实体，平铺调用loadCodec方法，该方法负责加载并返回对应的编解码器。
                .flatMap(this::loadCodec)
        );
    }


    /**
     * 处理透明消息编码实体删除事件。
     * 当一个或多个TransparentMessageCodecEntity被删除时，此方法被调用以异步处理这些实体的删除操作。
     * 它通过将实体集合转换为Flux流，并对每个实体调用removeCodec方法来实现异步处理。
     * 使用@EventListener注解将此方法绑定到特定的事件类型，以便在事件发生时自动调用。
     *
     * @param event 删除实体事件的实例，包含被删除的TransparentMessageCodecEntity实体集合。
     */
    @EventListener
    public void handleEntityEvent(EntityDeletedEvent<TransparentMessageCodecEntity> event) {
        // 异步处理实体删除事件，将实体集合转换为Flux流，然后对每个实体调用removeCodec方法
        event.async(
            Flux.fromIterable(event.getEntity())
                .flatMap(this::removeCodec)
        );
    }


    /**
     * 加载透明消息编码器实体。
     *
     * 本方法通过调用doLoadCodec方法来初始化编码器实体，随后使用EventBus发布一个加载编码器的事件。
     * 事件的路径为"/_sys/transparent-codec/load"，事件的载荷为传入的编码器实体。
     * 此方法的目的是为了在系统中动态加载和使用透明消息编码器。
     *
     * @param entity 透明消息编码器实体，包含编码器的相关配置和实现。
     * @return 返回一个Mono<Void>对象，表示异步加载编码器的操作结果。
     */
    public Mono<Void> loadCodec(TransparentMessageCodecEntity entity) {
        // 先调用doLoadCodec方法进行编码器的加载准备
        return doLoadCodec(entity)
            .then(
                // 然后使用EventBus发布一个事件，通知系统加载编码器实体
                eventBus
                    .publish("/_sys/transparent-codec/load", entity)
                    .then()
            );
    }

    /**
     * 删除透明消息编码实体。
     *
     * 本方法通过调用doRemoveCodec方法删除指定的透明消息编码实体，并在删除操作完成后，
     * 发布一个事件到事件总线，通知系统中相关的组件或服务关于编码实体的删除操作。
     * 这样做的目的是为了确保系统的数据一致性和实时性，使得所有相关的组件或服务都能
     * 及时了解到编码实体的变化情况，进而做出相应的处理或调整。
     *
     * @param entity 要删除的透明消息编码实体。
     * @return 返回一个Mono<Void>对象，表示异步删除操作的完成。
     */
    public Mono<Void> removeCodec(TransparentMessageCodecEntity entity) {
        // 执行删除操作，并链式调用then方法处理后续操作。
        return doRemoveCodec(entity)
            .then(
                // 在编码实体删除成功后，发布一个事件到事件总线。
                eventBus
                    .publish("/_sys/transparent-codec/removed", entity)
                    .then()
            );
    }

    /**
     * 当前方法用于启动一个查询流程，旨在加载透明设备消息编解码器。
     * 它从仓库中创建查询，尝试获取每个实体，并对每个实体尝试加载其编解码器。
     * 如果加载编解码器过程中发生错误，将记录错误信息并安全地跳过当前实体。
     *
     * @param args 命令行参数，可变参数，用于传递额外的配置或参数。
     * @throws Exception 如果查询或编解码器加载过程中发生无法恢复的错误。
     */
    @Override
    public void run(String... args) throws Exception {
        // 从仓库创建查询，查询所有实体
        repository
            .createQuery()
            // 获取每个实体
            .fetch()
            // 对每个实体进行操作
            .flatMap(e -> this
                // 尝试加载实体对应的编解码器
                .doLoadCodec(e)
                // 如果加载编解码器过程中发生错误，记录错误并返回空Mono，以跳过当前实体
                .onErrorResume(err -> {
                    log.error("load transparent device message codec [{}:{}] error", e.getId(), e.getProvider(), err);
                    return Mono.empty();
                }))
            // 订阅操作结果，不处理具体结果，主要用于启动查询流程
            .subscribe();
    }


    /**
     * CacheKey类用于作为缓存的键。
     * 该类通过 productId 和 deviceId 的组合来唯一标识一个缓存项，确保缓存的唯一性和查找效率。
     * 使用@Getter和@Setter注解，提供了对类中属性的自动getter和setter方法的生成，方便对属性的访问和设置。
     * 使用@EqualsAndHashCode注解，重写了对象的equals和hashCode方法，确保在比较两个CacheKey对象时，是基于productId和deviceId的比较，而非对象的引用比较。
     * 使用@AllArgsConstructor注解，生成了一个包含所有字段的构造函数，方便在创建CacheKey对象时初始化其属性。
     */
    @Getter
    @Setter
    @EqualsAndHashCode
    @AllArgsConstructor
    static class CacheKey {
        /**
         * 产品ID，用于标识缓存项所属的产品。
         */
        private String productId;
        /**
         * 设备ID，用于标识缓存项所属的设备。
         */
        private String deviceId;
    }
}
