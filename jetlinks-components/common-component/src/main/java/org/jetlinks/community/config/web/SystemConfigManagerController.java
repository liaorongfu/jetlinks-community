package org.jetlinks.community.config.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.SaveAction;
import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.community.ValueObject;
import org.jetlinks.community.config.ConfigManager;
import org.jetlinks.community.config.ConfigPropertyDef;
import org.jetlinks.community.config.ConfigScope;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/system/config")
@Resource(id = "system_config", name = "系统配置管理")
@AllArgsConstructor
@Tag(name = "系统配置管理")
public class SystemConfigManagerController {

    private final ConfigManager configManager;

    @GetMapping("/scopes")
    @QueryAction
    @Operation(summary = "获取配置作用域")
    public Flux<ConfigScope> getConfigScopes() {
        return configManager.getScopes();
    }

    /**
     * 获取指定作用域下的全部配置信息。
     *
     * @param scope 作用域标识，用于指定要获取配置信息的作用域。
     * @return 返回一个包含指定作用域下所有配置信息的Map对象。如果无法获取到配置信息，则返回一个空Map。
     */
    @GetMapping("/{scope}")
    @Authorize(ignore = true)
    @Operation(summary = "获取作用域下的全部配置信息")
    public Mono<Map<String, Object>> getConfigs(@PathVariable String scope) {
        // 首先验证用户是否已登录，然后根据作用域查询配置信息
        return Authentication
            .currentReactive()
            .hasElement()
            .flatMap(hasAuth -> configManager
                .getScope(scope)
                // 检查配置是否允许公共访问或者用户已登录
                .map(conf -> conf.isPublicAccess() || hasAuth)
                // 如果没有配置信息，默认用户已登录可以访问
                .defaultIfEmpty(hasAuth)
                .filter(Boolean::booleanValue)
                // 根据作用域获取配置属性
                .flatMap(ignore -> configManager.getProperties(scope))
                // 将配置属性转换为Map形式返回
                .map(ValueObject::values))
            // 如果没有可用的配置，则返回空Map
            .defaultIfEmpty(Collections.emptyMap());
    }

    /**
     * 获取指定作用域下的配置信息详情。
     *
     * 该接口通过GET请求，根据作用域(scope)参数获取相应的配置详细信息。
     * 返回值为配置项的Flux流，包含每个配置项的属性定义和值。
     *
     * @param scope 作用域标识，用于指定要获取配置信息的作用域。
     * @return Flux<ConfigPropertyValue> 配置项的值和定义的流，每个配置项包括属性定义和对应的值。
     */
    @GetMapping("/{scope}/_detail")
    @QueryAction
    @Operation(summary = "获取作用域下的配置信息")
    public Flux<ConfigPropertyValue> getConfigDetail(@PathVariable String scope) {
        // 从配置管理器中获取指定作用域的配置项值
        return configManager
            .getProperties(scope)
            .flatMapMany(values -> configManager
                // 获取配置项的定义，并结合配置项的值构造ConfigPropertyValue对象
                .getPropertyDef(scope)
                .map(def -> ConfigPropertyValue.of(def, values.get(def.getKey()).orElse(null))));
    }

    /**
     * 通过POST请求获取指定作用域下的配置详情。
     * 请求体中应包含一个字符串列表，列表中的每个字符串代表一个作用域。
     * 函数返回一个Flux，它会顺序地为每个作用域查询配置详情，并将作用域与配置信息一起返回。
     *
     * @param scopeMono 包含作用域列表的Mono对象。请求体应是一个JSON数组，每个元素是一个作用域的字符串。
     * @return Flux<Scope> 对象流，包含每个作用域及其对应的配置详情。
     */
    @PostMapping("/scopes")
    @QueryAction
    @Operation(summary = "获取作用域下的配置详情")
    public Flux<Scope> getConfigDetail(@RequestBody Mono<List<String>> scopeMono) {
        // 将Mono<List<String>>转换为Flux，然后对于每个作用域查询配置信息
        return scopeMono
            .flatMapMany(scopes -> Flux
                .fromIterable(scopes) // 将List<String>转换为Flux<String>
                .flatMap(scope -> getConfigs(scope) // 为每个作用域查询配置
                    .map(properties -> new Scope(scope, properties)))); // 将作用域和配置信息组合成Scope对象
    }

    /**
     * 保存配置到指定的作用域中。
     *
     * @param scope 指定配置的作用域，来自于URL路径变量。
     * @param properties 配置属性的流，包含需要保存的键值对，来自于请求体。
     * @return 返回一个空的Mono<Void>，表示异步操作完成。
     */
    @PostMapping("/{scope}")
    @SaveAction
    @Operation(summary = "保存配置")
    public Mono<Void> saveConfig(@PathVariable String scope,
                                 @RequestBody Mono<Map<String, Object>> properties) {
        // 将接收到的配置属性流设置到指定的作用域中
        return properties.flatMap(props -> configManager.setProperties(scope, props));
    }

    /**
     * 批量保存配置信息。
     * 该接口使用POST请求，路径为"/scope/_save"，用于接收并保存一系列配置信息。
     * 请求体应包含一个Flux类型的Scope对象流，允许服务端逐个处理这些配置对象。
     * 方法使用了Spring WebFlux框架的响应式编程模型，返回一个Mono<Void>，表示操作的完成。
     *
     * @param scope 一个Flux<Scope>对象，包含需要保存的一系列配置信息。
     * @return 返回一个Mono<Void>，表示异步操作完成，不返回任何结果。
     */
    @PostMapping("/scope/_save")
    @SaveAction
    @Operation(summary = "批量保存配置")
    @Transactional
    public Mono<Void> saveConfig(@RequestBody Flux<Scope> scope) {

        // 将传入的Scope对象流通过concatMap转换，逐个调用configManager的setProperties方法进行保存
        // 最后通过then方法表示所有保存操作完成
        return scope
            .concatMap(scopeConfig -> configManager.setProperties(scopeConfig.getScope(), scopeConfig.getProperties()))
            .then();
    }

    /**
     * ConfigPropertyValue 类继承自 ConfigPropertyDef，用于表示配置属性的值。
     * 该类添加了一个“值”字段，以及通过 FastBeanCopier 进行深度拷贝的构造方法。
     */
    @Getter
    @Setter
    public static class ConfigPropertyValue extends ConfigPropertyDef {
        private Object value;

        /**
         * 创建一个 ConfigPropertyValue 实例，通过从 ConfigPropertyDef 拷贝属性，并设置指定的值。
         * @param def ConfigPropertyDef 实例，用于拷贝属性定义。
         * @param value 想要设置的属性值。
         * @return 返回一个新的 ConfigPropertyValue 实例，包含拷贝的属性定义和设置的值。
         */
        public static ConfigPropertyValue of(ConfigPropertyDef def, Object value) {
            ConfigPropertyValue val = FastBeanCopier.copy(def, new ConfigPropertyValue());
            val.setValue(value);
            return val;
        }
    }

    /**
     * Scope 类用于定义作用域以及作用域内的属性集合。
     * 一个作用域可以包含多个属性，每个属性通过键值对进行标识和存储。
     */
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Scope {
        private String scope; // 作用域名称
        private Map<String, Object> properties; // 属性集合，以键值对形式存储
    }

}
