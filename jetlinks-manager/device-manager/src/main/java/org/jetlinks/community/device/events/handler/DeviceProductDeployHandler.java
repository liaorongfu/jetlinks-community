package org.jetlinks.community.device.events.handler;

import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.device.events.DeviceProductDeployEvent;
import org.jetlinks.community.device.service.LocalDeviceProductService;
import org.jetlinks.community.device.service.data.DeviceDataService;
import org.jetlinks.community.device.service.data.DeviceLatestDataService;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import org.jetlinks.core.metadata.DeviceMetadataCodec;
import org.jetlinks.supports.official.JetLinksDeviceMetadataCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PreDestroy;

/**
 * 处理设备产品发布事件
 * 设备管理服务类，负责设备的生命周期管理和数据处理。
 * 该类依赖于多个服务和工具，包括设备产品服务、设备元数据编码解码器、
 * 设备数据服务、设备最新数据服务和事件总线，用于实现设备的注册、数据更新和事件发布等功能。
 *
 * @author bsetfeng
 * @author zhouhao
 * @since 1.0
 **/
@Component
@Slf4j
@Order(1)
public class DeviceProductDeployHandler implements CommandLineRunner {

    /**
     * 设备产品服务，用于管理设备产品相关信息。
     */
    private final LocalDeviceProductService productService;

    /**
     * 设备元数据编码解码器，用于序列化和反序列化设备元数据。
     */
    private final DeviceMetadataCodec codec = new JetLinksDeviceMetadataCodec();

    /**
     * 设备数据服务，用于存储和查询设备的运行时数据。
     */
    private final DeviceDataService dataService;

    /**
     * 设备最新数据服务，用于存储和查询设备的最新数据。
     */
    private final DeviceLatestDataService latestDataService;

    /**
     * 事件总线，用于发布和订阅设备相关事件。
     */
    private final EventBus eventBus;

    /**
     * 可 disposable 的资源，用于管理需要自动释放的资源。
     */
    private final Disposable disposable;

    /**
     * DeviceProductDeployHandler的构造函数。
     *
     * 本构造函数用于初始化设备产品部署处理器，通过依赖注入方式传入以下服务：
     * 1. LocalDeviceProductService：用于本地设备产品相关的操作。
     * 2. DeviceDataService：用于设备数据的相关操作。
     * 3. EventBus：用于订阅和处理事件。
     * 4. DeviceLatestDataService：用于处理设备最新的数据服务。
     *
     * 在构造函数中，处理器会订阅特定的主题以监听产品元数据的升级事件，并在接收到事件后重新加载元数据。
     * 如果在重新加载元数据的过程中发生错误，会记录日志并忽略错误。
     *
     * @param productService 本地设备产品服务，用于产品相关的本地操作。
     * @param dataService 设备数据服务，用于处理设备数据。
     * @param eventBus 事件总线，用于订阅和发布事件。
     * @param latestDataService 设备最新数据服务，用于处理最新的设备数据。
     */
    @Autowired
    public DeviceProductDeployHandler(LocalDeviceProductService productService,
                                      DeviceDataService dataService,
                                      EventBus eventBus,
                                      DeviceLatestDataService latestDataService) {
        this.productService = productService;
        this.dataService = dataService;
        this.eventBus = eventBus;
        this.latestDataService = latestDataService;

        // 订阅产品元数据升级事件，事件主题为/_sys/product-upgrade。
        // 当收到事件时，会尝试重新加载元数据。如果加载失败，会记录警告日志并忽略错误。
        // 监听其他服务器上的物模型变更
        disposable = eventBus
            .subscribe(Subscription
                           .builder()
                           .subscriberId("product-metadata-upgrade")
                           .topics("/_sys/product-upgrade")
                           .justBroker()
                           .build(), String.class)
            .flatMap(id -> this
                .reloadMetadata(id)
                .onErrorResume((err) -> {
                    log.warn("handle product upgrade event error", err);
                    return Mono.empty();
                }))
            .subscribe();
    }


    /**
     * 在组件销毁前执行的生命周期方法。
     * 该方法的目的是为了在Spring管理的bean被销毁前，正确地清理资源。
     * 使用了JSR 330标准的@PreDestroy注解，确保在容器移除bean之前调用此方法。
     *
     * 主要操作是调用disposable.dispose()来取消订阅或清理之前注册的资源。
     * 这种做法常见于使用RxJava或其他类似库时，需要在组件生命周期结束时取消订阅，以避免内存泄漏。
     */
    @PreDestroy
    public void shutdown() {
        disposable.dispose();
    }

    /**
     * 处理设备产品部署事件。
     * 当设备产品部署事件发生时，此方法被调用以执行相应的处理逻辑。
     * 它首先异步注册设备的元数据，然后发布一个产品升级事件。
     * 使用异步处理方式，是为了确保事件处理不会阻塞当前线程，提高系统的响应性和吞吐量。
     *
     * @param event 事件对象，包含设备的ID和元数据信息。
     */
    @EventListener
    public void handlerEvent(DeviceProductDeployEvent event) {
        // 异步处理事件，以提高系统性能和响应性
        event.async(
            // 注册设备元数据，这是一个异步操作
            this.doRegisterMetadata(event.getId(), event.getMetadata())
                .then(
                    // 在元数据注册完成后，发布一个产品升级事件
                    eventBus.publish("/_sys/product-upgrade", event.getId())
                )
        );
    }


    /**
     * 重新加载指定产品的元数据。
     *
     * 此方法旨在异步重新获取并更新指定产品的元数据。它首先尝试根据产品ID查找产品，然后使用找到的产品的当前元数据来执行元数据的重新加载操作。
     * 重新加载操作的具体实现封装在 doReloadMetadata 方法中。完成后，不返回任何具体结果，仅表示操作已完成。
     *
     * @param productId 产品的唯一标识符，用于查找产品并重新加载其元数据。
     * @return Mono<Void> 表示异步操作完成的Mono实例，不返回任何具体结果。
     */
    protected Mono<Void> reloadMetadata(String productId) {
        // 根据产品ID查找产品
        return productService
            .findById(productId)
            // 使用找到的产品执行元数据重新加载操作
            .flatMap(product -> doReloadMetadata(productId, product.getMetadata()))
            // 表示所有操作已完成
            .then();
    }

    /**
     * 重新加载元数据的异步操作。
     *
     * 该方法使用给定的元数据字符串解码元数据，并针对给定的产品ID触发元数据的重新加载操作。
     * 它合并了两个不同的服务（dataService和latestDataService）的元数据重新加载操作，
     * 以确保两个操作都被执行，并且错误处理是延迟的，这意味着任何加载元数据过程中出现的错误都会被合并处理。
     *
     * @param productId 产品ID，用于指定要重新加载元数据的产品。
     * @param metadataString 元数据的字符串表示，需要先解码。
     * @return Mono<Void> 表示一个异步操作，该操作完成后不返回任何值。
     */
    protected Mono<Void> doReloadMetadata(String productId, String metadataString) {
        // 使用codec解码元数据字符串。
        return codec
            .decode(metadataString)
            .flatMap(metadata -> {
                // 合并两个服务的元数据重新加载操作，并延迟错误处理。
                // 这里使用mergeDelayError来合并Flux流，确保两个服务的调用都被执行，
                // 并且任何由于重新加载元数据而产生的错误都会被延迟处理。
                return Flux
                    .mergeDelayError(2,
                                     dataService.reloadMetadata(productId, metadata),
                                     latestDataService.reloadMetadata(productId, metadata))
                    .then();
            });
    }


    /**
     * 注册产品元数据。
     *
     * 该方法通过解码元数据字符串，然后同时注册元数据和升级元数据，最后返回一个Mono<Void>表示操作完成。
     * 使用Flux的mergeDelayError操作符将两个Flux合并，以便同时处理注册和升级操作。
     * 这种设计允许异步处理多个操作，并在所有操作完成后发出完成信号。
     *
     * @param productId 产品的唯一标识符，用于指定元数据所属的产品。
     * @param metadataString 待注册的元数据字符串，需要先进行解码。
     * @return Mono<Void> 表示注册操作的完成，没有返回值。
     */
    protected Mono<Void> doRegisterMetadata(String productId, String metadataString) {
        // 使用codec解码元数据字符串。
        return codec
            .decode(metadataString)
            .flatMap(metadata -> {
                // 合并注册元数据和升级元数据的Flux流，允许同时进行这两个操作。
                // mergeDelayError操作符用于合并流并延迟错误处理，以便在所有操作完成后统一处理错误。
                return Flux
                    .mergeDelayError(2,
                                     dataService.registerMetadata(productId, metadata),
                                     latestDataService.upgradeMetadata(productId, metadata))
                    .then();
            });
    }



    /**
     * 当前方法用于处理产品的注册逻辑，特别是针对状态为激活（State为1）的产品。
     * 它首先从产品服务中创建查询，然后筛选出状态为激活的产品，接着对这些产品进行元数据注册。
     * 如果在注册元数据过程中遇到错误，会记录警告日志，并继续处理下一个产品。
     *
     * @param args 命令行参数，未在此方法中直接使用，但作为Runnable接口实现的一部分必须存在。
     */
    @Override
    public void run(String... args) {
        // 从产品服务中创建查询
        productService
            .createQuery()
            // 获取所有状态为激活的产品
            .fetch()
            .filter(product -> new Byte((byte) 1).equals(product.getState()))
            // 对每个激活状态的产品，尝试注册其元数据
            .flatMap(deviceProductEntity -> this
                .doRegisterMetadata(deviceProductEntity.getId(), deviceProductEntity.getMetadata())
                // 如果注册元数据过程中发生错误，记录警告并跳过当前产品处理
                .onErrorResume(err -> {
                    log.warn("register product [{}] metadata error", deviceProductEntity.getId(), err);
                    return Mono.empty();
                })
            )
            // 订阅处理结果，确保所有激活产品的元数据都被尝试注册
            .subscribe();
    }

}
