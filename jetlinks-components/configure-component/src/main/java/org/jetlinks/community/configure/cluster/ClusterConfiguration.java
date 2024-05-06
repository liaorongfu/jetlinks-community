package org.jetlinks.community.configure.cluster;

import io.scalecube.cluster.ClusterConfig;
import io.scalecube.net.Address;
import io.scalecube.services.transport.rsocket.RSocketClientTransportFactory;
import io.scalecube.services.transport.rsocket.RSocketServerTransportFactory;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import io.scalecube.transport.netty.tcp.TcpTransportFactory;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.supports.cluster.redis.RedisClusterManager;
import org.jetlinks.supports.config.EventBusStorageManager;
import org.jetlinks.supports.event.InternalEventBus;
import org.jetlinks.supports.scalecube.ExtendedCluster;
import org.jetlinks.supports.scalecube.ExtendedClusterImpl;
import org.jetlinks.supports.scalecube.rpc.ScalecubeRpcManager;
import org.nustaq.serialization.FSTConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClusterProperties.class)
@ConditionalOnClass(ExtendedCluster.class)
public class ClusterConfiguration {

    /**
     * 创建并配置一个ExtendedClusterImpl实例。
     *
     * @param properties 包含集群配置属性的对象，如端口、成员别名、外部主机和外部端口等。
     * @param resourceLoader 用于加载类资源的资源加载器。
     * @return 配置好的ExtendedClusterImpl实例，已启动并准备就绪。
     */
    @Bean
    public ExtendedClusterImpl cluster(ClusterProperties properties, ResourceLoader resourceLoader) {

        // 创建FST消息编解码器，用于序列化和反序列化消息。
        FSTMessageCodec codec = new FSTMessageCodec(() -> {
            FSTConfiguration configuration = FSTConfiguration
                .createDefaultConfiguration()
                .setForceSerializable(true); // 强制可序列化设置。

            configuration.setClassLoader(resourceLoader.getClassLoader()); // 设置适当的类加载器。
            return configuration;
        });

        // 创建ExtendedClusterImpl实例并配置其各种属性，如传输层、成员别名、外部访问信息等。
        ExtendedClusterImpl impl = new ExtendedClusterImpl(
            new ClusterConfig()
                .transport(conf -> conf
                    .port(properties.getPort()) // 设置监听端口。
                    .messageCodec(codec) // 设置消息编解码器。
                    .transportFactory(new TcpTransportFactory())) // 使用TCP作为传输协议。
                .memberAlias(properties.getId()) // 设置成员别名。
                .externalHost(properties.getExternalHost()) // 设置外部主机地址。
                .externalPort(properties.getExternalPort()) // 设置外部端口。
                .membership(conf -> conf // 配置成员资格，包括初始种子成员列表。
                    .seedMembers(properties
                                     .getSeeds()
                                     .stream()
                                     .map(Address::from)
                                     .collect(Collectors.toList()))


                )

        );
        impl.startAwait(); // 启动集群实例并等待其就绪。
        return impl;
    }


    /**
     * 创建并返回一个InternalEventBus实例。
     * 这个方法没有参数。
     *
     * @return InternalEventBus - 返回一个新的InternalEventBus实例。
     */
    @Bean
    public InternalEventBus eventBus() {
        return new InternalEventBus();
    }

    /**
     * 创建并返回一个EventBusStorageManager实例。
     * 这个方法配置了EventBusStorageManager以使用提供的ClusterManager和EventBus实例。
     * 特别地，它设置了一个特殊的标识符（-1）作为参数之一，这可能用于标识或配置EventBusStorageManager的特定行为。
     *
     * @param clusterManager 用于集群管理的ClusterManager实例，它帮助管理应用的集群配置和状态。
     * @param eventBus 事件总线实例，用于应用内部或跨应用的事件通信。
     * @return 返回配置好的EventBusStorageManager实例。
     */
    @Bean
    public EventBusStorageManager eventBusStorageManager(ClusterManager clusterManager, EventBus eventBus) {
        return new EventBusStorageManager(clusterManager,
                                          eventBus,
                                          -1);
    }


    /**
     * 创建并返回一个RedisClusterManager实例。
     * 该方法配置了RedisClusterManager Bean，在Spring容器初始化时调用其startup方法进行初始化。
     *
     * @param properties Redis集群的配置属性，包括集群名称和ID等信息。
     * @param template 反应式Redis模板，用于与Redis进行异步交互。
     * @return RedisClusterManager实例，用于管理Redis集群连接。
     */
    @Bean(initMethod = "startup")
    public RedisClusterManager clusterManager(ClusterProperties properties, ReactiveRedisTemplate<Object, Object> template) {
        // 使用提供的配置属性和反应式Redis模板创建RedisClusterManager实例
        return new RedisClusterManager(properties.getName(), properties.getId(), template);
    }

    /**
     * 创建并配置 ScalecubeRpcManager 实例。
     * 该方法通过使用 ScalecubeRpcManager 来初始化和管理服务的RSocket传输层，同时配置外部访问的主机和端口。
     *
     * @param cluster 代表集群状态和配置的ExtendedCluster实例。
     * @param properties 包含集群和RPC相关配置的ClusterProperties实例。
     * @return 配置好的ScalecubeRpcManager实例，负责RPC服务的管理和传输。
     */
    @Bean(initMethod = "startAwait", destroyMethod = "stopAwait")
    public ScalecubeRpcManager rpcManager(ExtendedCluster cluster, ClusterProperties properties) {
        // 创建ScalecubeRpcManager实例，并配置RSocket服务的服务器和客户端传输工厂
        return new ScalecubeRpcManager(cluster,
                                       () -> new RSocketServiceTransport()
                                           .serverTransportFactory(RSocketServerTransportFactory.tcp(properties.getRpcPort()))
                                           .clientTransportFactory(RSocketClientTransportFactory.tcp()))
            // 配置外部访问的主机和端口
            .externalHost(properties.getRpcExternalHost())
            .externalPort(properties.getRpcExternalPort());
    }

}
