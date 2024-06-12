package org.jetlinks.community.device.measurements;

import lombok.Generated;
import org.jetlinks.community.dashboard.DashboardObject;
import org.jetlinks.community.dashboard.Measurement;
import org.jetlinks.community.dashboard.ObjectDefinition;
import org.jetlinks.community.device.service.data.DeviceDataService;
import org.jetlinks.core.device.DeviceProductOperator;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.metadata.DeviceMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
/**
 * 设备仪表盘对象类，实现了仪表盘对象接口。
 * 该类负责汇总设备的相关数据，包括事件、属性和元数据，用于展示在设备仪表盘上。
 * @author Rabbuse
 */
public class DeviceDashboardObject implements DashboardObject {
    private final String id;

    private final String name;

    private final DeviceProductOperator productOperator;

    private final EventBus eventBus;

    private final DeviceDataService deviceDataService;

    private final DeviceRegistry registry;
    /**
     * 私有构造方法，用于实例化DeviceDashboardObject。
     *
     * @param id                   设备ID
     * @param name                 设备名称
     * @param productOperator      设备产品操作符，用于操作设备产品级别的信息
     * @param eventBus             事件总线，用于发布和订阅事件
     * @param dataService          设备数据服务，用于处理设备数据
     * @param registry             设备注册表，用于管理设备的注册和发现
     */
    private DeviceDashboardObject(String id, String name,
                                  DeviceProductOperator productOperator,
                                  EventBus eventBus,
                                  DeviceDataService dataService,
                                  DeviceRegistry registry) {
        this.id = id;
        this.name = name;
        this.productOperator = productOperator;
        this.eventBus = eventBus;
        this.deviceDataService = dataService;
        this.registry = registry;
    }

    /**
     * 创建一个DeviceDashboardObject实例。
     *
     * 该方法提供了一个方便的方式来初始化DeviceDashboardObject对象，无需直接调用构造函数。
     * 它接受设备的相关参数，包括设备ID、设备名称、产品操作员、事件总线、数据服务和注册服务，
     * 通过这些参数来构建一个完整的DeviceDashboardObject实例。
     *
     * @param id 设备的唯一标识符。
     * @param name 设备的名称。
     * @param productOperator 与设备产品相关的操作员，用于处理设备产品相关的操作。
     * @param eventBus 用于发布和订阅设备相关事件的事件总线。
     * @param dataService 提供设备数据存储和检索服务的接口。
     * @param registry 负责设备注册和管理的服务。
     * @return 新创建的DeviceDashboardObject实例。
     */
    public static DeviceDashboardObject of(String id, String name,
                                           DeviceProductOperator productOperator,
                                           EventBus eventBus,
                                           DeviceDataService dataService,
                                           DeviceRegistry registry) {
        return new DeviceDashboardObject(id, name, productOperator, eventBus, dataService, registry);
    }


    /**
     * 获取对象定义。
     *
     * 本方法旨在提供一个对象定义的实例，该定义包含了对象的标识和名称。
     * 通过返回一个实现了ObjectDefinition接口的匿名类实例，实现了对该对象定义的封装。
     *
     * @return ObjectDefinition 对象定义的实例，包含了id和name属性。
     */
    @Override
    public ObjectDefinition getDefinition() {
        return new ObjectDefinition() {
            /**
             * 获取对象定义的标识。
             *
             * 该方法是ObjectDefinition接口的一部分，用于获取特定对象的唯一标识。
             * 在这里，我们返回的是在实例化时指定的id值。
             *
             * @return String 对象的唯一标识。
             */
            @Override
            @Generated
            public String getId() {
                return id;
            }

            /**
             * 获取对象定义的名称。
             *
             * 该方法是ObjectDefinition接口的一部分，用于获取特定对象的名称。
             * 在这里，我们返回的是在实例化时指定的name值。
             *
             * @return String 对象的名称。
             */
            @Override
            @Generated
            public String getName() {
                return name;
            }
        };
    }


    /**
     * 获取设备测量值的流。
     *
     * 该方法通过组合不同的测量值生成器，来创建一个测量值的流。它包括了设备的事件测量、属性测量、
     * 所有事件的综合测量和所有属性的综合测量。这些测量值是基于设备的数据服务和事件总线进行生成的。
     *
     * @return 返回一个包含各种设备测量值的Flux流。
     */
    @Override
    public Flux<Measurement> getMeasurements() {
        // 使用Flux.concat来组合多个Flux流，这些流分别代表不同类型的测量值。
        return Flux
            .concat(
                // 生成设备事件测量值的流。它首先从产品操作符中获取设备元数据，然后迭代事件列表，
                // 为每个事件创建一个DeviceEventMeasurement实例。
                productOperator
                    .getMetadata()
                    .flatMapIterable(DeviceMetadata::getEvents)
                    .map(event -> new DeviceEventMeasurement(productOperator.getId(),
                                                             eventBus,
                                                             event,
                                                             deviceDataService)),

                // 创建一个设备属性测量值的单个实例流。它使用产品操作符的ID、事件总线、
                // 设备数据服务和注册表来生成一个DevicePropertiesMeasurement实例。
                Mono.just(new DevicePropertiesMeasurement(productOperator.getId(),
                                                          eventBus,
                                                          deviceDataService,
                                                          registry)),

                // 生成设备所有事件的综合测量值的流。它从产品操作符的元数据中直接创建一个DeviceEventsMeasurement实例。
                productOperator
                    .getMetadata()
                    .map(metadata -> new DeviceEventsMeasurement(productOperator.getId(),
                                                                 eventBus,
                                                                 metadata,
                                                                 deviceDataService)),

                // 生成设备所有属性的综合测量值的流。它首先从产品操作符的元数据中迭代所有属性，
                // 然后为每个属性创建一个DevicePropertyMeasurement实例。
                productOperator
                    .getMetadata()
                    .flatMapIterable(DeviceMetadata::getProperties)
                    .map(event -> new DevicePropertyMeasurement(productOperator.getId(),
                                                                eventBus,
                                                                event,
                                                                deviceDataService))
            );
    }


    /**
     * 根据ID获取测量值。
     *
     * 此方法根据提供的ID来决定返回哪种类型的测量值。支持的ID包括"properties"和"events"，
     * 以及特定的事件或属性ID。根据ID的不同，方法将返回对应的设备属性测量值、设备事件测量值
     * 或特定事件/属性的测量值。
     *
     * @param id 测量值的唯一标识符。可以是"properties"、"events"，或者是特定事件或属性的ID。
     * @return 返回一个Mono对象，包含所请求的测量值。
     */
    @Override
    public Mono<Measurement> getMeasurement(String id) {
        // 当ID为"properties"时，直接返回设备属性测量值
        if ("properties".equals(id)) {
            return Mono.just(new DevicePropertiesMeasurement(productOperator.getId(), eventBus, deviceDataService, registry));
        }
        // 当ID为"events"时，根据元数据创建并返回设备事件测量值
        if ("events".equals(id)) {
            return productOperator
                .getMetadata()
                .map(metadata -> new DeviceEventsMeasurement(productOperator.getId(), eventBus, metadata, deviceDataService));
        }
        // 对于其他ID，尝试根据ID获取特定事件的测量值；如果获取不到，则尝试获取特定属性的测量值
        return productOperator
            .getMetadata()
            .flatMap(metadata -> Mono.justOrEmpty(metadata.getEvent(id)))
            .<Measurement>map(event -> new DeviceEventMeasurement(productOperator.getId(), eventBus, event, deviceDataService))
            // 如果特定事件的测量值获取不到，则尝试获取特定属性的测量值
            // 事件没获取到则尝试获取属性
            .switchIfEmpty(productOperator
                               .getMetadata()
                               .flatMap(metadata -> Mono.justOrEmpty(metadata.getProperty(id)))
                               .map(event -> new DevicePropertyMeasurement(productOperator.getId(), eventBus, event, deviceDataService)));
    }

}
