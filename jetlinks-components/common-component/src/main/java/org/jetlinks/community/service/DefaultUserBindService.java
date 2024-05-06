package org.jetlinks.community.service;

import lombok.AllArgsConstructor;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.exception.BusinessException;
import org.hswebframework.web.id.IDGenerator;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * 使用Reactive Redis实现的用户绑定服务类。
 * @author bestfeng
 */
@AllArgsConstructor
public class DefaultUserBindService implements UserBindService {

    /**
     * Redis响应式操作接口，用于与Redis进行数据交互。
     */
    private final ReactiveRedisOperations<Object, Object> redis;

    /**
     * 生成用户绑定码。
     * @param userInfo 用户信息，包含需要绑定的用户详情。
     * @return 返回生成的用户绑定码。
     */
    @Override
    public Mono<String> generateBindCode(UserInfo userInfo) {
        // 生成绑定码并设置到Redis中，有效期为1分钟
        String code = UserBindService.USER_BIND_CODE_PRE + IDGenerator.MD5.generate();
        return redis
            .opsForValue()
            .set(code, userInfo, Duration.ofMinutes(1))
            .thenReturn(code);
    }

    /**
     * 通过绑定码获取用户信息。
     * @param bindCode 用户的绑定码。
     * @return 返回匹配的用户信息Mono。如果找不到绑定信息，返回错误Mono。
     */
    @Override
    public Mono<UserInfo> getUserInfoByCode(String bindCode) {
        // 从Redis中获取用户信息，并在获取成功后删除该绑定码
        ReactiveValueOperations<Object, Object> operations = redis.opsForValue();
        return operations
            .get(bindCode)
            .cast(UserInfo.class)
            .flatMap(userInfo -> operations.delete(bindCode).thenReturn(userInfo))
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.user_binding_code_incorrect")));
    }

    /**
     * 校验用户绑定关系。
     * @param authentication 认证信息，包含当前登录的用户信息。
     * @param userInfo 需要校验绑定关系的用户信息。
     * @throws BusinessException 如果绑定关系非法，则抛出业务异常。
     */
    public void checkUserBind(Authentication authentication, UserInfo userInfo){
        // 校验登录用户与待绑定用户是否为同一人，不一致则抛出异常
        if (!Objects.equals(authentication.getUser().getId(), userInfo.getUserId())){
            throw new BusinessException("error.illegal_bind");
        }
    }


}
