package org.jetlinks.community.network.mqtt.client;

import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
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
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * MQTT Client 网络组件提供商
 *
 * @author zhouhao
 * @since 1.0
 */
@Component
@Slf4j
@ConfigurationProperties(prefix = "jetlinks.network.mqtt-client")
/**
 * MqttClientProvider类负责提供MQTT客户端连接服务。
 * 它实现了NetworkProvider接口，以支持创建和管理MQTT客户端网络连接。
 */
public class MqttClientProvider implements NetworkProvider<MqttClientProperties> {

    private final Vertx vertx;

    private final CertificateManager certificateManager;

    private final Environment environment;

    @Getter
    @Setter
    private MqttClientOptions template = new MqttClientOptions();


    /**
     * 构造函数初始化MQTT客户端提供者。
     * @param certificateManager 证书管理器，用于处理TLS连接的证书。
     * @param vertx Vertx实例，用于创建MQTT客户端。
     * @param environment 环境变量，用于配置MQTT客户端选项。
     */
    public MqttClientProvider(CertificateManager certificateManager,
                              Vertx vertx,
                              Environment environment) {
        this.vertx = vertx;
        this.certificateManager = certificateManager;
        this.environment = environment;
        template.setTcpKeepAlive(true);
        template.setAutoKeepAlive(true);
        template.setKeepAliveInterval(180);
    }

    @Nonnull
    @Override
    public NetworkType getType() {
        return DefaultNetworkType.MQTT_CLIENT;
    }

    /**
     * 创建一个新的MQTT网络连接。
     * @param properties MQTT客户端属性，用于配置MQTT连接。
     * @return 返回一个表示MQTT网络连接的Mono对象。
     */
    @Nonnull
    @Override
    public Mono<Network> createNetwork(@Nonnull MqttClientProperties properties) {
        VertxMqttClient mqttClient = new VertxMqttClient(properties.getId());
        return initMqttClient(mqttClient, properties);
    }

    /**
     * 重新加载MQTT网络连接配置。
     * @param network 当前网络连接。
     * @param properties 新的MQTT客户端属性。
     * @return 返回一个表示更新后的MQTT网络连接的Mono对象。
     */
    @Override
    public Mono<Network> reload(@Nonnull Network network, @Nonnull MqttClientProperties properties) {
        VertxMqttClient mqttClient = ((VertxMqttClient) network);
        if (mqttClient.isLoading()) {
            return Mono.just(mqttClient);
        }
        return initMqttClient(mqttClient, properties);
    }

    /**
     * 初始化MQTT客户端。
     * @param mqttClient MQTT客户端实例。
     * @param properties MQTT客户端属性。
     * @return 返回一个表示初始化后的MQTT网络连接的Mono对象。
     */
    public Mono<Network> initMqttClient(VertxMqttClient mqttClient, MqttClientProperties properties) {
        return convert(properties)
            .map(options -> {
                mqttClient.setTopicPrefix(properties.getTopicPrefix());
                mqttClient.setLoading(true);
                MqttClient client = MqttClient.create(vertx, options);
                mqttClient.setClient(client);
                // 连接到MQTT服务器
                client.connect(properties.getRemotePort(), properties.getRemoteHost(), result -> {
                    mqttClient.setLoading(false);
                    if (!result.succeeded()) {
                        log.warn("connect mqtt [{}@{}:{}] error",
                                 properties.getClientId(),
                                 properties.getRemoteHost(),
                                 properties.getRemotePort(),
                                 result.cause());
                    } else {
                        log.debug("connect mqtt [{}] success", properties.getId());
                    }
                });
                return mqttClient;
            });
    }

    /**
     * 获取配置元数据。
     * @return 返回描述MQTT客户端配置的元数据的ConfigMetadata对象。
     */
    @Nullable
    @Override
    public ConfigMetadata getConfigMetadata() {
        return new DefaultConfigMetadata()
            .add("id", "id", "", new StringType())
            .add("remoteHost", "远程地址", "", new StringType())
            .add("remotePort", "远程地址", "", new IntType())
            .add("certId", "证书id", "", new StringType())
            .add("secure", "开启TSL", "", new BooleanType())
            .add("clientId", "客户端ID", "", new BooleanType())
            .add("username", "用户名", "", new BooleanType())
            .add("password", "密码", "", new BooleanType());
    }

    /**
     * 创建MQTT客户端配置。
     * @param properties 网络属性，用于初始化MQTT客户端配置。
     * @return 返回一个表示配置好的MQTT客户端属性的Mono对象。
     */
    @Nonnull
    @Override
    public Mono<MqttClientProperties> createConfig(@Nonnull NetworkProperties properties) {
        return Mono
            .defer(() -> {
                MqttClientProperties config = FastBeanCopier.copy(properties.getConfigurations(), new MqttClientProperties());
                config.setId(properties.getId());
                config.validate();
                return Mono.just(config);
            })
            .as(LocaleUtils::transform);
    }


    /**
     * 转换MQTT客户端属性到选项。
     * @param config MQTT客户端配置。
     * @return 返回一个表示配置好的MQTT客户端选项的Mono对象。
     */
    private Mono<MqttClientOptions> convert(MqttClientProperties config) {
        MqttClientOptions options = FastBeanCopier.copy(config, new MqttClientOptions(template));

        // 设置客户端ID、用户名、密码
        String clientId = String.valueOf(config.getClientId());
        String username = config.getUsername();
        String password = config.getPassword();

        options.setClientId(clientId);
        options.setPassword(password);
        options.setUsername(username);

        // 如果启用了TLS，配置证书
        if (config.isSecure()) {
            options.setSsl(true);
            return certificateManager
                .getCertificate(config.getCertId())
                .map(VertxKeyCertTrustOptions::new)
                .doOnNext(options::setKeyCertOptions)
                .doOnNext(options::setTrustOptions)
                .thenReturn(options);
        }
        return Mono.just(options);
    }

    @Override
    public boolean isReusable() {
        return true;
    }
}

