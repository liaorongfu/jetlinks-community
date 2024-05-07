package org.jetlinks.community.network.mqtt.server.vertx;

import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.bean.FastBeanCopier;
import org.hswebframework.web.i18n.LocaleUtils;
import org.jetlinks.community.network.*;
import org.jetlinks.core.metadata.ConfigMetadata;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.BooleanType;
import org.jetlinks.core.metadata.types.IntType;
import org.jetlinks.core.metadata.types.StringType;
import org.jetlinks.community.network.security.CertificateManager;
import org.jetlinks.community.network.security.VertxKeyCertTrustOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConfigurationProperties(prefix = "jetlinks.network.mqtt-server")
public class DefaultVertxMqttServerProvider implements NetworkProvider<VertxMqttServerProperties> {

    private final CertificateManager certificateManager;
    private final Vertx vertx;

    @Getter
    @Setter
    private MqttServerOptions template = new MqttServerOptions();

    public DefaultVertxMqttServerProvider(CertificateManager certificateManager, Vertx vertx) {
        this.certificateManager = certificateManager;
        this.vertx = vertx;
        template.setTcpKeepAlive(true);
    }

    /**
     * 获取网络类型的方法。
     * 这个方法重写了getType方法，用于返回当前网络类型的枚举值。
     *
     * @return NetworkType 返回网络类型的枚举对象。在这个实现中，总是返回DefaultNetworkType.MQTT_SERVER。
     */
    @Nonnull
    @Override
    public NetworkType getType() {
        return DefaultNetworkType.MQTT_SERVER;
    }

    /**
     * 创建一个网络实例。
     * 该函数初始化并配置一个Vert.x Mqtt服务器，准备其用于接收和处理Mqtt协议的连接。
     *
     * @param properties 服务器配置属性，包含必要的配置信息，如服务器ID等。
     * @return 返回一个包含初始化好的Vert.x Mqtt服务器的Mono对象。
     */
    @Nonnull
    @Override
    public Mono<Network> createNetwork(@Nonnull VertxMqttServerProperties properties) {
        // 创建一个新的Vert.x Mqtt服务器实例，使用提供的服务器ID进行初始化。
        VertxMqttServer server = new VertxMqttServer(properties.getId());
        // 初始化并配置服务器，应用所有相关的配置属性。
        return initServer(server, properties);
    }

    /**
     * 初始化MQTT服务器实例。
     * 此方法将根据提供的配置属性初始化一个或多个MQTT服务器实例，并绑定到指定的地址和端口上。
     * 如果配置中指定了实例数量大于1，则会创建多个MQTT服务器实例。
     * 每个实例都会尝试监听指定的端口，成功或失败的信息将被记录。
     *
     * @param server 未初始化的MQTT服务器实例，此方法将对其进行配置和初始化。
     * @param properties MQTT服务器的配置属性，包括实例数量、绑定地址和端口等。
     * @return 配置完成并初始化后的MQTT服务器实例。
     */
    private Mono<Network> initServer(VertxMqttServer server, VertxMqttServerProperties properties) {
        // 确保实例数量至少为1
        int numberOfInstance = Math.max(1, properties.getInstance());
        return convert(properties)
            .map(options -> {
                // 创建MQTT服务器实例列表
                List<MqttServer> instances = new ArrayList<>(numberOfInstance);
                for (int i = 0; i < numberOfInstance; i++) {
                    MqttServer mqttServer = MqttServer.create(vertx, options);
                    instances.add(mqttServer);
                }
                // 设置服务器绑定地址和端口，并设置MQTT服务器实例列表
                server.setBind(new InetSocketAddress(options.getHost(), options.getPort()));
                server.setMqttServer(instances);
                // 对每个实例执行监听操作
                for (MqttServer instance : instances) {
                   vertx.nettyEventLoopGroup()
                       .execute(()->{
                           instance.listen(result -> {
                               // 监听成功或失败的处理
                               if (result.succeeded()) {
                                   log.debug("startup mqtt server [{}] on port :{} ", properties.getId(), result
                                       .result()
                                       .actualPort());
                               } else {
                                   server.setLastError(result.cause().getMessage());
                                   log.warn("startup mqtt server [{}] error ", properties.getId(), result.cause());
                               }
                           });
                       });
                }
                return server;
            });

    }


    /**
     * 重新加载MQTT服务器配置。
     * 该方法会使用提供的新配置重新初始化已经存在的MQTT服务器实例。
     *
     * @param network 当前的MQTT网络实例，需要被重新加载配置。
     * @param properties 新的服务器配置属性，用于重新配置MQTT服务器。
     * @return 返回一个包含重新配置后的MQTT网络实例的Mono对象。
     */
    @Override
    public Mono<Network> reload(@Nonnull Network network, @Nonnull VertxMqttServerProperties properties) {
        log.debug("reload mqtt server[{}]", properties.getId()); // 打印重新加载MQTT服务器的debug信息
        return initServer((VertxMqttServer) network, properties); // 初始化MQTT服务器
    }

    /**
     * 获取配置元数据信息。
     * 该方法重写了getConfigMetadata方法，用于返回一个包含配置项名称、描述、默认值及其类型的信息对象。
     *
     * @return ConfigMetadata 配置元数据对象。返回的配置元数据包含了一系列配置项，
     *         包括id、host、port、publicHost、publicPort、certId、secure和maxMessageSize等。
     *         每个配置项都有其名称、描述、默认值和类型。其中，secure配置项被重复添加了两次，可能是一个错误。
     */
    @Override
    public ConfigMetadata getConfigMetadata() {

        // 创建并初始化DefaultConfigMetadata对象，添加配置项
        return new DefaultConfigMetadata()
            .add("id", "id", "", new StringType())
            .add("host", "本地地址", "", new StringType())
            .add("port", "本地端口", "", new IntType())
            .add("publicHost", "公网地址", "", new StringType())
            .add("publicPort", "公网端口", "", new IntType())
            .add("certId", "证书id", "", new StringType())
            .add("secure", "开启TSL", "", new BooleanType())
            .add("secure", "开启TSL", "", new BooleanType())
            .add("maxMessageSize", "最大消息长度", "", new StringType());
    }

    /**
     * 创建Vertx Mqtt服务器配置。
     * 这个方法通过接受网络属性，生成并返回一个Vertx Mqtt服务器配置对象。
     *
     * @param properties 网络属性，包含配置信息和ID。
     * @return 返回一个包含Vertx Mqtt服务器配置的Mono对象。
     */
    @Nonnull
    @Override
    public Mono<VertxMqttServerProperties> createConfig(@Nonnull NetworkProperties properties) {
        // 延迟创建Vertx Mqtt服务器配置
        return Mono.defer(() -> {
            // 使用FastBeanCopier从网络属性的配置中复制到新的Vertx Mqtt服务器配置对象
            VertxMqttServerProperties config = FastBeanCopier.copy(properties.getConfigurations(), new VertxMqttServerProperties());
            // 设置配置ID
            config.setId(properties.getId());
            // 验证配置的合法性
            config.validate();
            // 返回包含配置的Mono对象
            return Mono.just(config);
        })
            // 对Mono对象应用Locale转换
            .as(LocaleUtils::transform);
    }

    /**
     * 将 VertxMqttServerProperties 对象转换为 MqttServerOptions 对象。
     * 这个方法主要用于配置 MQTT 服务器的选项，如端口、主机地址以及消息的最大大小。
     * 如果 properties 中设置为安全连接（isSecure() 返回 true），则会配置 SSL/TLS 连接。
     *
     * @param properties MQTT 服务器的配置属性，包含端口、主机地址、最大消息大小等设置。
     * @return 返回配置好的 MqttServerOptions 对象，如果配置了安全连接，还会包括证书信息。
     */
    private Mono<MqttServerOptions> convert(VertxMqttServerProperties properties) {
        MqttServerOptions options = new MqttServerOptions(template); // 使用模板初始化 MqttServerOptions
        options.setPort(properties.getPort()); // 设置端口
        options.setHost(properties.getHost()); // 设置主机地址
        options.setMaxMessageSize(properties.getMaxMessageSize()); // 设置最大消息大小

        if (properties.isSecure()) { // 如果配置了安全连接
            options.setSsl(true); // 启用 SSL/TLS
            // 获取证书并配置到 MqttServerOptions 中
            return certificateManager
                .getCertificate(properties.getCertId())
                .map(VertxKeyCertTrustOptions::new)
                .doOnNext(options::setKeyCertOptions)
                .doOnNext(options::setTrustOptions)
                .thenReturn(options);
        }
        // 如果没有配置安全连接，直接返回配置好的 MqttServerOptions 对象
        return Mono.just(options);
    }


    /**
     * 判断当前对象是否可以重复使用。
     * 这个方法是接口方法的实现，用于指示当前对象是否能够在处理多个请求或任务时重复使用。
     *
     * @return boolean - 如果对象可以重复使用，则返回true；否则返回false。
     *         在这个实现中，总是返回true，表示对象可以被重复使用。
     */
    @Override
    public boolean isReusable() {
        return true;
    }
}
