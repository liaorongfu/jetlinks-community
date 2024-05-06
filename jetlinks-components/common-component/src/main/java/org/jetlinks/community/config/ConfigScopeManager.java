package org.jetlinks.community.config;

import java.util.List;

/**
 * ConfigScopeManager接口定义了管理配置作用域的行为。
 * 该接口提供了一个方法来添加一个新的配置作用域及其属性。
 */
public interface ConfigScopeManager {

    /**
     * 添加一个配置作用域和它的属性列表。
     *
     * @param scope 表示要添加的作用域。
     * @param properties 该作用域下要添加的属性列表。
     *                   属性定义了配置项的元数据，例如名称、类型等。
     */
    void addScope(ConfigScope scope, List<ConfigPropertyDef> properties);

}

