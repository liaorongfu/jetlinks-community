package org.jetlinks.community.config;

import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.bean.FastBeanCopier;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于配置系统作用域属性的类，可以通过属性文件中"system.config"前缀来配置。
 */
@ConfigurationProperties(prefix = "system.config")
public class ConfigScopeProperties implements ConfigScopeCustomizer{

    /**
     * 作用域列表，每个作用域包含一组配置属性。
     */
    @Getter
    @Setter
    private List<Scope> scopes = new ArrayList<>();

    /**
     * 自定义配置作用域。
     * @param manager 配置作用域管理器，用于添加自定义配置作用域。
     */
    @Override
    public void custom(ConfigScopeManager manager) {
        // 遍历所有配置的作用域，并将其添加到管理器中
        for (Scope scope : scopes) {
            manager.addScope(FastBeanCopier.copy(scope,new ConfigScope()), scope.properties);
        }
    }

    /**
     * 配置作用域内部类，包含一组配置属性定义。
     */
    @Getter
    @Setter
    public static class Scope extends ConfigScope {
        // 配置作用域内的属性定义列表
        private List<ConfigPropertyDef> properties = new ArrayList<>();
    }

}

