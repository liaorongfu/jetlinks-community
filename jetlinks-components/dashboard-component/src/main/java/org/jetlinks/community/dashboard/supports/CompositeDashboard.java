package org.jetlinks.community.dashboard.supports;

import lombok.Getter;
import org.jetlinks.community.dashboard.Dashboard;
import org.jetlinks.community.dashboard.DashboardDefinition;
import org.jetlinks.community.dashboard.DashboardObject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 一个复合仪表盘类，实现了Dashboard接口，用于管理和提供仪表盘上的对象和数据。
 */
class CompositeDashboard implements Dashboard {

    /**
     * 仪表盘的定义信息。
     */
    @Getter
    private DashboardDefinition definition;

    /**
     * 构造函数，初始化一个复合仪表盘实例。
     *
     * @param definition 仪表盘的定义信息。
     */
    public CompositeDashboard(DashboardDefinition definition) {
        this.definition = definition;
    }

    // 用于存储静态仪表盘对象的ConcurrentHashMap。
    private Map<String, DashboardObject> staticObjects = new ConcurrentHashMap<>();

    // 用于存储静态仪表盘的CopyOnWriteArrayList。
    private List<Dashboard> staticDashboard = new CopyOnWriteArrayList<>();

    /**
     * 向仪表盘添加一个测量数据提供者。
     *
     * @param provider 测量数据的提供者。
     */
    public void addProvider(MeasurementProvider provider) {
        // 如果对象已存在，则将其增强为Composite类型，否则创建新的Composite类型对象。
        DashboardObject object = staticObjects.computeIfAbsent(provider.getObjectDefinition().getId(), __ -> new CompositeDashboardObject());
        if(object instanceof CompositeDashboardObject){
            CompositeDashboardObject compose = ((CompositeDashboardObject) object);
            compose.addProvider(provider);
        }
    }

    /**
     * 向仪表盘添加一个子仪表盘。
     *
     * @param dashboard 要添加的子仪表盘。
     */
    public void addDashboard(Dashboard dashboard){
        staticDashboard.add(dashboard);
    }

    /**
     * 向仪表盘添加一个对象。
     *
     * @param object 要添加的仪表盘对象。
     */
    public void addObject(DashboardObject object) {
        staticObjects.put(object.getDefinition().getId(), object);
    }

    /**
     * 获取仪表盘上的所有对象。
     *
     * @return 返回一个Flux流，包含所有仪表盘对象。
     */
    @Override
    public Flux<DashboardObject> getObjects() {
        // 合并静态对象和静态仪表盘中的所有对象。
        return Flux.concat(
            Flux.fromIterable(staticObjects.values()),
            Flux.fromIterable(staticDashboard).flatMap(Dashboard::getObjects));
    }

    /**
     * 根据ID获取仪表盘对象。
     *
     * @param id 对象的ID。
     * @return 返回一个Mono流，包含找到的对象，如果没有找到则返回空。
     */
    @Override
    public Mono<DashboardObject> getObject(String id) {
        // 尝试从静态对象中直接获取，如果未找到则从静态仪表盘中查找。
        return Mono.justOrEmpty(staticObjects.get(id))
            .switchIfEmpty(Mono.defer(()-> Flux.fromIterable(staticDashboard)
                .flatMap(dashboard -> dashboard.getObject(id))
                .next()));
    }
}

