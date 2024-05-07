package org.jetlinks.community.dashboard.supports;

import org.jetlinks.community.dashboard.Dashboard;
import org.jetlinks.community.dashboard.DashboardDefinition;
import org.jetlinks.community.dashboard.DashboardManager;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的仪表盘管理器，负责管理和维护仪表盘信息。
 * 同时实现BeanPostProcessor接口，以便于在Spring容器中监听Bean的初始化过程，
 * 并自动注册测量提供者和仪表盘。
 */
@Component
public class DefaultDashboardManager implements DashboardManager, BeanPostProcessor {

    // 用于存储仪表盘的ConcurrentHashMap，支持并发访问和修改。
    private Map<String, CompositeDashboard> dashboards = new ConcurrentHashMap<>();

    /**
     * 获取所有仪表盘的Flux流。
     *
     * @return 返回一个包含所有仪表盘的Flux流。
     */
    @Override
    public Flux<Dashboard> getDashboards() {
        return Flux.fromIterable(dashboards.values());
    }

    /**
     * 根据ID获取特定的仪表盘。
     *
     * @param id 仪表盘的唯一标识。
     * @return 如果找到，则返回对应的仪表盘Mono，否则返回空Mono。
     */
    @Override
    public Mono<Dashboard> getDashboard(String id) {
        return Mono.justOrEmpty(dashboards.get(id));
    }

    /**
     * 向管理器中添加一个测量数据提供者，并根据提供者的定义创建或获取对应的仪表盘，
     * 然后将该提供者添加到该仪表盘中。
     *
     * @param provider 测量数据的提供者。
     */
    private void addProvider(MeasurementProvider provider) {

        DashboardDefinition definition = provider.getDashboardDefinition();

        // 根据提供者的定义获取或创建仪表盘，然后将提供者添加到仪表盘中。
        CompositeDashboard dashboard = dashboards.computeIfAbsent(definition.getId(), __ -> new CompositeDashboard(definition));

        dashboard.addProvider(provider);
    }

    /**
     * 向管理器中添加一个仪表盘。首先根据仪表盘的定义创建或获取对应的CompositeDashboard，
     * 然后将该仪表盘添加到这个CompositeDashboard中。
     *
     * @param dashboard 需要被添加的仪表盘实例。
     */
    private void addDashboard(Dashboard dashboard) {

        // 根据仪表盘的定义获取或创建对应的CompositeDashboard，然后将该仪表盘添加到其中。
        CompositeDashboard cached = dashboards.computeIfAbsent(dashboard.getDefinition().getId(), __ -> new CompositeDashboard(dashboard.getDefinition()));

        cached.addDashboard(dashboard);

    }

    /**
     * Spring Bean初始化后的处理过程。在此方法中会检查当前初始化的Bean是否为MeasurementProvider或Dashboard类型，
     * 如果是，则分别调用addProvider或addDashboard方法进行注册。
     *
     * @param bean 当前正在初始化的Bean实例。
     * @param beanName 当前Bean的名称。
     * @return 返回经过可能修改后的Bean实例。
     * @throws BeansException 如果处理过程中发生错误。
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

        // 检查Bean类型并进行相应的注册操作。
        if (bean instanceof MeasurementProvider) {
            addProvider(((MeasurementProvider) bean));
        } else if (bean instanceof Dashboard) {
            addDashboard(((Dashboard) bean));
        }
        return bean;
    }
}

