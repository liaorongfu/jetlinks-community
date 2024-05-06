package org.jetlinks.community.configure.device;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.guava.CaffeinatedGuava;
import org.hswebframework.web.crud.annotation.EnableEasyormRepository;
import org.jetlinks.core.ProtocolSupports;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.core.config.ConfigStorageManager;
import org.jetlinks.core.device.DeviceOperationBroker;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.device.DeviceStateChecker;
import org.jetlinks.core.device.session.DeviceSessionManager;
import org.jetlinks.core.message.interceptor.DeviceMessageSenderInterceptor;
import org.jetlinks.core.rpc.RpcManager;
import org.jetlinks.core.server.MessageHandler;
import org.jetlinks.supports.cluster.ClusterDeviceOperationBroker;
import org.jetlinks.supports.cluster.ClusterDeviceRegistry;
import org.jetlinks.supports.cluster.RpcDeviceOperationBroker;
import org.jetlinks.supports.scalecube.ExtendedCluster;
import org.jetlinks.supports.server.ClusterSendToDeviceMessageHandler;
import org.jetlinks.supports.server.DecodedClientMessageHandler;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * DeviceClusterConfiguration类负责定义设备集群的配置。
 * 它只在存在ProtocolSupports bean时条件成立，配置包括设备注册表、拦截器注册、设备会话管理和消息处理器。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(ProtocolSupports.class)
public class DeviceClusterConfiguration {

    /**
     * 定义并创建设备注册表Bean。
     *
     * @param supports 用于检查协议支持的类。
     * @param manager 设备集群的管理器。
     * @param storageManager 配置存储管理器。
     * @param handler 设备操作的处理器。
     * @return 返回一个初始化好的ClusterDeviceRegistry实例。
     */
    @Bean
    public ClusterDeviceRegistry deviceRegistry(ProtocolSupports supports,
                                                ClusterManager manager,
                                                ConfigStorageManager storageManager,
                                                DeviceOperationBroker handler) {

        // 创建设备注册表并使用Caffeine构建缓存
        return new ClusterDeviceRegistry(supports,
                                         storageManager,
                                         manager,
                                         handler,
                                         CaffeinatedGuava.build(Caffeine.newBuilder()));
    }

    /**
     * 条件性地创建一个BeanPostProcessor，用于注册设备消息拦截器和设备状态检查器。
     *
     * @param registry 设备注册表。
     * @return 返回一个自定义的BeanPostProcessor实例，用于后置处理bean。
     */
    @Bean
    @ConditionalOnBean(ClusterDeviceRegistry.class)
    public BeanPostProcessor interceptorRegister(ClusterDeviceRegistry registry) {
        return new BeanPostProcessor() {
            /**
             * 在bean初始化后处理bean，用于注册拦截器和状态检查器。
             *
             * @param bean 待处理的bean实例。
             * @param beanName bean的名称。
             * @return 返回处理后的bean实例。
             * @throws BeansException 如果处理中发生错误。
             */
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                // 注册设备消息拦截器和设备状态检查器
                if (bean instanceof DeviceMessageSenderInterceptor) {
                    registry.addInterceptor(((DeviceMessageSenderInterceptor) bean));
                }
                if (bean instanceof DeviceStateChecker) {
                    registry.addStateChecker(((DeviceStateChecker) bean));
                }
                return bean;
            }
        };
    }

    /**
     * 定义设备会话管理器的Bean，配置基于属性的持久化设备会话管理。
     *
     * @param rpcManager RPC管理器。
     * @return 返回一个初始化好的PersistenceDeviceSessionManager实例。
     */
    @Bean(initMethod = "init", destroyMethod = "shutdown")
    @ConditionalOnBean(RpcManager.class)
    @ConfigurationProperties(prefix = "device.session.persistence")
    public PersistenceDeviceSessionManager deviceSessionManager(RpcManager rpcManager) {

        // 创建并配置设备会话管理器
        return new PersistenceDeviceSessionManager(rpcManager);
    }

    /**
     * 条件性地创建默认的发送到设备的消息处理器。
     *
     * @param sessionManager 设备会话管理器。
     * @param registry 设备注册表。
     * @param messageHandler 消息处理器。
     * @param clientMessageHandler 解码的客户端消息处理器。
     * @return 返回一个初始化好的ClusterSendToDeviceMessageHandler实例。
     */
    @Bean
    public ClusterSendToDeviceMessageHandler defaultSendToDeviceMessageHandler(DeviceSessionManager sessionManager,
                                                                               DeviceRegistry registry,
                                                                               MessageHandler messageHandler,
                                                                               DecodedClientMessageHandler clientMessageHandler) {
        // 创建并配置集群发送到设备的消息处理器
        return new ClusterSendToDeviceMessageHandler(sessionManager, messageHandler, registry, clientMessageHandler);
    }

    /**
     * 创建RPC设备操作Broker的Bean。
     *
     * @param rpcManager RPC管理器。
     * @param sessionManager 设备会话管理器。
     * @return 返回一个初始化好的RpcDeviceOperationBroker实例。
     */
    @Bean
    public RpcDeviceOperationBroker rpcDeviceOperationBroker(RpcManager rpcManager,
                                                             DeviceSessionManager sessionManager) {
        // 创建RPC设备操作Broker
        return new RpcDeviceOperationBroker(rpcManager, sessionManager);
    }


}

