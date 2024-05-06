package org.jetlinks.community.reactorql;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.reactor.ql.ReactorQLContext;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * ReactorQLFactoryBean类，实现了FactoryBean接口、InitializingBean接口和Ordered接口。
 * 用于创建目标类的代理实例，该代理实例能够动态地处理方法调用。
 */
public class ReactorQLFactoryBean implements FactoryBean<Object>, InitializingBean, Ordered {

    /**
     * 目标类的Class对象。
     */
    @Getter
    @Setter
    private Class<?> target;

    /**
     * 目标类的代理实例。
     */
    private Object proxy;

    /**
     * 参数名发现器，用于获取方法参数的名称。
     */
    private static final ParameterNameDiscoverer nameDiscoverer = new LocalVariableTableParameterNameDiscoverer();

    /**
     * 类构造函数，无参构造。
     */
    public ReactorQLFactoryBean() {

    }

    /**
     * 获取代理对象。
     *
     * @return 返回代理对象。
     */
    @Override
    public Object getObject() {
        return proxy;
    }

    /**
     * 获取对象类型。
     *
     * @return 返回目标类的Class对象。
     */
    @Override
    public Class<?> getObjectType() {
        return target;
    }

    /**
     * 在所有属性设置完成后调用，用于初始化代理对象。
     */
    @Override
    public void afterPropertiesSet() {
        // 使用ConcurrentHashMap来缓存方法与调用处理器的映射关系
        Map<Method, Function<Object[], Object>> cache = new ConcurrentHashMap<>();

        // 创建代理实例
        this.proxy = Proxy
            .newProxyInstance(ClassUtils.getDefaultClassLoader(),
                              new Class[]{target},
                              (proxy, method, args) ->
                                  cache
                                      .computeIfAbsent(method, mtd -> createInvoker(target, mtd, mtd.getAnnotation(ReactorQL.class)))
                                      .apply(args));
    }

    /**
     * 创建方法调用的处理器。
     *
     * @param type       目标类。
     * @param method     被调用的方法。
     * @param reactorQL  方法上的ReactorQL注解。
     * @return 返回方法调用的处理器。
     * @throws Throwable 抛出异常。
     */
    @SneakyThrows
    private Function<Object[], Object> createInvoker(Class<?> type, Method method, ReactorQL reactorQL) {
        // 处理默认方法或无ReactorQL注解的方法
        if (method.isDefault() || reactorQL == null) {
            Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class
                .getDeclaredConstructor(Class.class);
            constructor.setAccessible(true);
            MethodHandles.Lookup lookup = constructor.newInstance(type);
            MethodHandle handle = lookup
                .in(type)
                .unreflectSpecial(method, type)
                .bindTo(proxy);
            return args -> {
                try {
                    return handle.invokeWithArguments(args);
                } catch (Throwable e) {
                    return Mono.error(e);
                }
            };
        }

        // 处理有ReactorQL注解的方法
        ResolvableType returnType = ResolvableType.forMethodReturnType(method);
        if (returnType.toClass() != Mono.class && returnType.toClass() != Flux.class) {
            throw new UnsupportedOperationException("方法返回值必须为Mono或者Flux");
        }
        Class<?> genericType = returnType.getGeneric(0).toClass();
        Function<Map<String, Object>, ?> mapper;

        // 根据返回类型选择合适的映射器
        if (genericType == Map.class || genericType == Object.class) {
            mapper = Function.identity();
        } else {
            mapper = map -> FastBeanCopier.copy(map, genericType);
        }

        Function<Flux<?>, Publisher<?>> resultMapper =
            returnType.resolve() == Mono.class
                ? flux -> flux.take(1).singleOrEmpty()
                : flux -> flux;

        // 解析方法参数名
        String[] names = nameDiscoverer.getParameterNames(method);

        try {
            // 创建ReactorQL实例并初始化上下文
            org.jetlinks.reactor.ql.ReactorQL reactorQLInstance =
                org.jetlinks.reactor.ql.ReactorQL
                    .builder()
                    .sql(reactorQL.value())
                    .build();

            return args -> {
                Map<String, Object> argsMap = new HashMap<>();
                ReactorQLContext context = ReactorQLContext.ofDatasource(name -> {
                    if (args.length == 0) {
                        return Flux.just(1);
                    }
                    if (args.length == 1) {
                        return convertToFlux(args[0]);
                    }
                    return convertToFlux(argsMap.get(name));
                });
                // 绑定参数到上下文
                for (int i = 0; i < args.length; i++) {
                    String indexName = "arg" + i;

                    String name = names == null ? indexName : names[i];
                    context.bind(i, args[i]);
                    context.bind(name, args[i]);
                    context.bind(indexName, args[i]);
                    argsMap.put(names == null ? indexName : names[i], args[i]);
                    argsMap.put(indexName, args[i]);
                }
                // 执行ReactorQL表达式并映射结果
                return reactorQLInstance.start(context)
                                        .map(record -> mapper.apply(record.asMap()))
                                        .as(resultMapper);
            };
        } catch (Throwable e) {
            throw new IllegalArgumentException(
                "create ReactorQL method [" + method + "] error,sql:\n" + (String.join(" ", reactorQL.value())), e);
        }
    }

    /**
     * 将对象转换为Flux流。
     *
     * @param arg 待转换的对象。
     * @return 返回转换后的Flux流。
     */
    protected Flux<Object> convertToFlux(Object arg) {
        if (arg == null) {
            return Flux.empty();
        }
        if (arg instanceof Publisher) {
            return Flux.from((Publisher<?>) arg);
        }
        if (arg instanceof Iterable) {
            return Flux.fromIterable((Iterable<?>) arg);
        }
        if (arg instanceof Object[]) {
            return Flux.fromArray((Object[]) arg);
        }
        return Flux.just(arg);
    }

    /**
     * 获取顺序值。
     *
     * @return 返回Ordered.LOWEST_PRECEDENCE，表示最低优先级。
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

