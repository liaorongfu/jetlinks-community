package org.jetlinks.community.authorize;

import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.DefaultDimensionType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 授权规格类, 用于定义权限检查的具体规格。
 */
@Getter
@Setter
public class AuthenticationSpec implements Serializable {

    private static final long serialVersionUID = 3512105446265694264L;

    /**
     * 角色规格, 用于定义角色相关的授权信息。
     */
    private RoleSpec role;

    /**
     * 权限规格列表, 用于定义具体权限相关的授权信息。
     */
    private List<PermissionSpec> permissions;

    /**
     * 角色规格类, 包含角色ID列表。
     */
    @Getter
    @Setter
    public static class RoleSpec {
        /**
         * 角色ID列表。
         */
        private List<String> idList;
    }

    /**
     * 权限规格类, 包含权限ID和操作列表。
     */
    @Getter
    @Setter
    public static class PermissionSpec implements Serializable {
        private static final long serialVersionUID = 7188197046015343251L;

        /**
         * 权限ID。
         */
        private String id;

        /**
         * 权限所包含的操作列表。
         */
        private List<String> actions;
    }

    /**
     * 检查给定的认证信息是否满足授权规格。
     *
     * @param auth 认证信息
     * @return 如果认证信息满足授权规格则返回true, 否则返回false。
     */
    public boolean isGranted(Authentication auth) {
        return createFilter().test(auth);
    }

    /**
     * 创建一个权限检查的断言。
     *
     * @return 返回一个Predicate, 用于对Authentication对象进行授权检查。
     */
    public Predicate<Authentication> createFilter() {
        RoleSpec role = this.role;
        List<PermissionSpec> permissions = this.permissions;
        List<Predicate<Authentication>> all = new ArrayList<>();

        // 为角色规格添加断言, 如果存在角色ID列表。
        if (null != role && role.getIdList() != null) {
            all.add(auth -> auth.hasDimension(DefaultDimensionType.role.getId(), role.getIdList()));
        }

        // 为权限规格列表中的每个权限添加断言。
        if (null != permissions) {
            for (PermissionSpec permission : permissions) {
                all.add(auth -> auth.hasPermission(permission.getId(), permission.getActions()));
            }
        }

        // 组合所有断言为一个复合断言。
        Predicate<Authentication> temp = null;
        for (Predicate<Authentication> predicate : all) {
            if (temp == null) {
                temp = predicate;
            } else {
                temp = temp.and(predicate);
            }
        }
        // 如果没有断言则返回总是为真的断言。
        return temp == null ? auth -> true : temp;
    }
}

