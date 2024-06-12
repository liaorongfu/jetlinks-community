package org.jetlinks.community.device.configuration;

import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.ezorm.rdb.operator.DatabaseOperator;
import org.jetlinks.community.buffer.BufferProperties;
import org.jetlinks.community.device.entity.DeviceInstanceEntity;
import org.jetlinks.community.device.function.ReactorQLDeviceSelectorBuilder;
import org.jetlinks.community.device.function.RelationDeviceSelectorProvider;
import org.jetlinks.community.device.message.DeviceMessageConnector;
import org.jetlinks.community.device.message.writer.TimeSeriesMessageWriterConnector;
import org.jetlinks.community.device.service.data.*;
import org.jetlinks.community.rule.engine.executor.DeviceSelectorBuilder;
import org.jetlinks.community.rule.engine.executor.device.DeviceSelectorProvider;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.device.session.DeviceSessionManager;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.server.MessageHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 设备管理配置类，用于配置设备相关的服务和组件。
 * @author Rabbuse
 */
@Configuration
@EnableConfigurationProperties(DeviceDataStorageProperties.class)
public class DeviceManagerConfiguration {

    /**
     * 创建设备选择器提供者，用于提供设备选择策略。
     *
     * @return DeviceSelectorProvider 实例
     */
    @Bean
    public DeviceSelectorProvider relationSelectorProvider() {
        return new RelationDeviceSelectorProvider();
    }

    /**
     * 创建设备选择器构建器，用于构建设备选择器。
     *
     * @param deviceRepository 设备仓库，用于存储和查询设备信息。
     * @param deviceRegistry 设备注册表，用于注册和管理设备。
     * @return DeviceSelectorBuilder 实例
     */
    @Bean
    public DeviceSelectorBuilder deviceSelectorBuilder(ReactiveRepository<DeviceInstanceEntity, String> deviceRepository,
                                                       DeviceRegistry deviceRegistry) {
        return new ReactorQLDeviceSelectorBuilder(deviceRegistry, deviceRepository);
    }

    /**
     * 创建一个DeviceMessageConnector实例，用于处理设备消息。
     * <p>
     * DeviceMessageConnector在设备管理和消息处理中起着关键作用。它负责监听设备消息，
     * 并通过提供的消息处理器来处理这些消息。此外，它还利用会话管理器和设备注册表来管理
     * 设备的会话状态和注册信息。
     *
     * @param eventBus 用于发布和订阅事件的事件总线。DeviceMessageConnector将利用它来发布
     *                 与设备消息相关的事件。
     * @param messageHandler 用于处理设备消息的具体逻辑。DeviceMessageConnector将设备消息转发
     *                       给这个处理器进行处理。
     * @param sessionManager 用于管理设备会话。DeviceMessageConnector利用它来跟踪设备的连接
     *                       状态和会话信息。
     * @param registry 设备注册表，用于存储和检索设备信息。DeviceMessageConnector使用它来
     *                 注册新设备或查询已注册设备的信息。
     * @return DeviceMessageConnector的实例，用于处理设备消息。
     */
    @Bean
    public DeviceMessageConnector deviceMessageConnector(EventBus eventBus,
                                                         MessageHandler messageHandler,
                                                         DeviceSessionManager sessionManager,
                                                         DeviceRegistry registry) {
        // 创建并返回DeviceMessageConnector实例
        return new DeviceMessageConnector(eventBus, registry, messageHandler, sessionManager);
    }


    /**
     * 根据配置条件创建时间序列消息写入连接器。
     * <p>
     * 此方法使用条件注解@ConditionalOnProperty来检查配置属性"device.message.writer.time-series.enabled"的值。
     * 如果该属性值为true，或者该属性不存在，则会创建并返回一个TimeSeriesMessageWriterConnector实例。
     * 这种条件配置允许在不需要时间序列消息写入功能时，不实例化这个连接器，从而节省资源。
     *
     * @param dataService 用于与设备数据交互的服务，提供数据存储和检索的能力。
     * @return TimeSeriesMessageWriterConnector的实例，用于写入时间序列数据。
     * @see ConditionalOnProperty
     */
    @Bean
    @ConditionalOnProperty(prefix = "device.message.writer.time-series", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TimeSeriesMessageWriterConnector timeSeriesMessageWriterConnector(DeviceDataService dataService) {
        return new TimeSeriesMessageWriterConnector(dataService);
    }


    /**
     * 内部配置类，用于配置设备最新数据存储相关属性。
     */
    @Configuration
    @ConditionalOnProperty(prefix = "jetlinks.device.storage", name = "enable-last-data-in-db", havingValue = "true")
    static class DeviceLatestDataServiceConfiguration {

        /**
         * 配置设备最新数据缓冲区属性。
         *
         * @return BufferProperties 实例，配置了缓冲区的路径、大小、并行度和超时时间。
         */
        @Bean
        @ConfigurationProperties(prefix = "jetlinks.device.storage.latest.buffer")
        public BufferProperties deviceLatestDataServiceBufferProperties() {
            BufferProperties bufferProperties = new BufferProperties();
            bufferProperties.setFilePath("./data/device-latest-data-buffer");
            bufferProperties.setSize(1000);
            bufferProperties.setParallelism(1);
            bufferProperties.setTimeout(Duration.ofSeconds(1));
            return bufferProperties;
        }

        /**
         * 创建一个数据库设备最新数据服务实例。
         * 该服务负责在数据库中存储和管理设备的最新数据。使用数据库操作器来执行实际的数据库操作，
         * 并利用设备最新数据服务缓冲区属性来优化数据处理流程。
         *
         * @param databaseOperator 提供数据库操作接口的实例，用于与数据库交互。
         * @return DatabaseDeviceLatestDataService 实例，用于处理设备的最新数据存储。
         */
        @Bean(destroyMethod = "destroy")
        public DatabaseDeviceLatestDataService deviceLatestDataService(DatabaseOperator databaseOperator) {
            return new DatabaseDeviceLatestDataService(databaseOperator,
                                                       deviceLatestDataServiceBufferProperties());
        }


    }

    /**
     * 根据配置条件和Bean存在性决定是否创建DeviceLatestDataService的bean。
     * <p>
     * 此方法只有在以下条件满足时才会创建NonDeviceLatestDataService的bean：
     * 1. 配置属性"jetlinks.device.storage.enable-last-data-in-db"的值为false，或者该属性不存在。
     * 2. Spring上下文中不存在名为DeviceLatestDataService的bean。
     * <p>
     * 这种条件性的bean创建允许灵活地根据配置决定是否启用特定的服务实现。
     * 当启用此配置时，它提供了一个默认的实现，该实现可能不将设备的最新数据存储在数据库中。
     *
     * @return DeviceLatestDataService的实例，实际上返回的是NonDeviceLatestDataService的实例。
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "jetlinks.device.storage",
        name = "enable-last-data-in-db",
        havingValue = "false",
        matchIfMissing = true)
    @ConditionalOnMissingBean(DeviceLatestDataService.class)
    public DeviceLatestDataService deviceLatestDataService() {
        return new NonDeviceLatestDataService();
    }


}

