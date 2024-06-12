package org.jetlinks.community.device.measurements;

import org.jetlinks.community.dashboard.DashboardObject;
import org.jetlinks.community.device.entity.DeviceProductEntity;
import org.jetlinks.community.device.service.LocalDeviceProductService;
import org.jetlinks.community.device.service.data.DeviceDataService;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.event.EventBus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;

/**
 * 实现设备仪表盘功能的组件，提供动态数据展示。
 */
@Component
public class DeviceDynamicDashboard implements DeviceDashboard {

    /**
     * 本地设备产品服务，用于查询和操作设备产品信息。
     */
    private final LocalDeviceProductService productService;

    /**
     * 设备注册表，用于查询和管理设备。
     */
    private final DeviceRegistry registry;

    /**
     * 事件总线，用于发布和订阅事件。
     */
    private final EventBus eventBus;

    /**
     * 设备数据服务，用于存储和检索设备数据。
     */
    private final DeviceDataService dataService;

    /**
     * 构造函数，初始化DeviceDynamicDashboard依赖的服务。
     *
     * @param productService 本地设备产品服务
     * @param registry       设备注册表
     * @param deviceDataService 设备数据服务
     * @param eventBus       事件总线
     */
    public DeviceDynamicDashboard(LocalDeviceProductService productService,
                                  DeviceRegistry registry,
                                  DeviceDataService deviceDataService,
                                  EventBus eventBus) {
        this.productService = productService;
        this.registry = registry;
        this.eventBus = eventBus;
        this.dataService = deviceDataService;
    }

    /**
     * 初始化方法，用于组件启动后的初始化操作。
     */
    @PostConstruct
    public void init() {
        // 设备状态变更初始化操作
    }

    /**
     * 获取所有设备仪表盘对象的流。
     *
     * @return 包含所有设备仪表盘对象的Flux流
     */
    @Override
    public Flux<DashboardObject> getObjects() {
        return productService.createQuery()
            .fetch()
            .flatMap(this::convertObject);
    }

    /**
     * 根据ID获取单个设备仪表盘对象。
     *
     * @param id 设备ID
     * @return 包含指定设备仪表盘对象的Mono流
     */
    @Override
    public Mono<DashboardObject> getObject(String id) {
        return productService.findById(id)
            .flatMap(this::convertObject);
    }

    /**
     * 将设备产品实体转换为设备仪表盘对象。
     *
     * @param product 设备产品实体
     * @return 转换后的设备仪表盘对象的Mono流
     */
    protected Mono<DeviceDashboardObject> convertObject(DeviceProductEntity product) {
        return registry.getProduct(product.getId())
            .map(operator -> DeviceDashboardObject.of(product.getId(), product.getName(), operator, eventBus, dataService, registry));
    }
}

