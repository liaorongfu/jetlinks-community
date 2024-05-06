package org.jetlinks.community.strategy;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 一个静态策略管理器，用于管理和获取策略对象。
 * 支持并发添加和获取策略，确保线程安全。
 *
 * @param <S> 策略的类型，必须继承自Strategy
 */
public class StaticStrategyManager<S
    extends Strategy>
    implements StrategyManager<S> {

    // 使用ConcurrentHashMap来存储策略ID和对应的策略Mono对象，以支持并发操作。
    private final Map<String, Mono<S>> strategies = new ConcurrentHashMap<>();

    /**
     * 添加一个策略到管理器中。
     *
     * @param strategy 策略对象，不能为null。
     */
    public void addStrategy(S strategy) {
        this.addStrategy(strategy.getId(), Mono.just(strategy));
    }

    /**
     * 添加一个策略到管理器中，使用Mono来延迟获取策略对象。
     *
     * @param strategyId 策略的唯一标识符，不能为null或空。
     * @param providerMono 提供策略对象的Mono，用于异步获取策略。
     */
    public void addStrategy(String strategyId, Mono<S> providerMono) {
        strategies.put(strategyId, providerMono);
    }

    /**
     * 根据策略ID获取策略的Mono对象。
     * 如果策略ID不存在，返回空的Mono对象。
     *
     * @param strategyId 策略的唯一标识符。
     * @return 策略的Mono对象，如果找不到则返回空的Mono。
     */
    @Override
    public final Mono<S> getStrategy(String strategyId) {
        return strategies.getOrDefault(strategyId, Mono.empty());
    }

    /**
     * 获取所有策略的Flux流。
     *
     * @return 所有策略的Flux流。
     */
    @Override
    public final Flux<S> getStrategies() {
        // 将所有策略的Mono对象合并为一个Flux流返回。
        return Flux.concat(strategies.values());
    }

    /**
     * 基于给定的策略ID和函数，使用Mono执行一些操作。
     * 如果策略不存在，则执行onStrategyNotFound方法。
     *
     * @param strategy 策略ID。
     * @param executor 一个函数，接受策略对象，返回一个Mono<T>。
     * @param <T> 返回值类型。
     * @return 执行结果的Mono对象。
     */
    protected final <T> Mono<T> doWithMono(String strategy, Function<S, Mono<T>> executor) {
        // 获取策略并执行提供的函数，如果策略不存在则调用onStrategyNotFound。
        return this
            .getStrategy(strategy)
            .switchIfEmpty(onStrategyNotFound(strategy))
            .flatMap(executor);
    }

    /**
     * 基于给定的策略ID和函数，使用Flux执行一些操作。
     * 如果策略不存在，则执行onStrategyNotFound方法。
     *
     * @param strategy 策略ID。
     * @param executor 一个函数，接受策略对象，返回一个Flux<T>。
     * @param <T> 返回值类型。
     * @return 执行结果的Flux对象。
     */
    protected final <T> Flux<T> doWithFlux(String strategy, Function<S, Flux<T>> executor) {
        // 获取策略并执行提供的函数，如果策略不存在则调用onStrategyNotFound。
        return this
            .getStrategy(strategy)
            .switchIfEmpty(onStrategyNotFound(strategy))
            .flatMapMany(executor);
    }

    /**
     * 当策略未找到时调用的方法，可被子类重写提供自定义行为。
     * 默认实现返回一个空的Mono对象。
     *
     * @param strategy 策略ID，未找到的策略。
     * @param <T> 返回值类型。
     * @return 一个空的Mono对象。
     */
    protected <T> Mono<T> onStrategyNotFound(String strategy){
        return Mono.empty();
    }
}
