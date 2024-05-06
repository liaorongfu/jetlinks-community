package org.jetlinks.community.config;

import lombok.AllArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.jetlinks.community.ValueObject;
import org.jetlinks.community.config.entity.ConfigEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单配置管理器，实现了配置管理和配置作用域管理接口。
 * 用于管理和存储配置作用域及其属性定义。
 */
@AllArgsConstructor
public class SimpleConfigManager implements ConfigManager, ConfigScopeManager {

    // 使用ConcurrentHashMap来存储配置作用域和其属性定义，以支持并发操作。
    private final Map<ConfigScope, Set<ConfigPropertyDef>> scopes = new ConcurrentHashMap<>();

    // 用于存储和检索配置实体的反应式仓库。
    private final ReactiveRepository<ConfigEntity, String> repository;

    /**
     * 添加一个配置作用域及其属性定义。
     *
     * @param scope 配置作用域，不可为null。
     * @param properties 配置作用域内的属性定义列表，不可为null。
     */
    @Override
    public void addScope(ConfigScope scope,
                         List<ConfigPropertyDef> properties) {
        scopes.computeIfAbsent(scope, ignore -> new LinkedHashSet<>())
              .addAll(properties);
    }

    /**
     * 获取所有配置作用域。
     *
     * @return 返回一个包含所有配置作用域的Flux流。
     */
    @Override
    public Flux<ConfigScope> getScopes() {
        return Flux.fromIterable(scopes.keySet());
    }

    /**
     * 根据作用域ID获取对应配置作用域。
     *
     * @param scope 配置作用域的ID，不可为null。
     * @return 返回匹配的配置作用域的Mono流。如果没有找到，则返回空的Mono。
     */
    @Override
    public Mono<ConfigScope> getScope(String scope) {
        return this
            .getScopes()
            .filter(configScope -> Objects.equals(configScope.getId(), scope))
            .take(1)
            .singleOrEmpty();
    }

    /**
     * 根据作用域获取该作用域下的所有属性定义。
     *
     * @param scope 配置作用域的ID，不可为null。
     * @return 返回一个包含指定作用域下所有属性定义的Flux流。
     */
    @Override
    public Flux<ConfigPropertyDef> getPropertyDef(String scope) {
        return Flux.fromIterable(scopes.getOrDefault(
            ConfigScope.of(scope, scope, false),
            Collections.emptySet()));
    }

    /**
     * 获取指定作用域下的所有配置属性值。
     *
     * @param scope 配置作用域的ID，不可为null。
     * @return 返回一个包含指定作用域下所有配置属性的ValueObject的Mono流。
     */
    @Override
    public Mono<ValueObject> getProperties(String scope) {
        return Mono
            .zip(
                // 获取默认属性值
                getPropertyDef(scope)
                    .filter(def -> null != def.getDefaultValue())
                    .collectMap(ConfigPropertyDef::getKey, ConfigPropertyDef::getDefaultValue),
                // 获取数据库中的配置属性值
                repository
                    .createQuery()
                    .where(ConfigEntity::getScope, scope)
                    .fetch()
                    .filter(val -> MapUtils.isNotEmpty(val.getProperties()))
                    .<Map<String, Object>>reduce(new LinkedHashMap<>(), (l, r) -> {
                        l.putAll(r.getProperties());
                        return l;
                    }),
                // 合并默认值和数据库值
                (defaults, values) -> {
                    defaults.forEach(values::putIfAbsent);
                    return values;
                }
            ).map(ValueObject::of);
    }

    /**
     * 设置指定作用域下的配置属性值。
     *
     * @param scope 配置作用域的ID，不可为null。
     * @param values 要设置的配置属性键值对，不可为null。
     * @return 返回一个表示操作完成的Mono<Void>流。
     */
    @Override
    public Mono<Void> setProperties(String scope, Map<String, Object> values) {
        ConfigEntity entity = new ConfigEntity();
        entity.setProperties(values);
        entity.setScope(scope);
        // 生成新的配置实体ID并保存
        return repository.save(entity).then();
    }

}

