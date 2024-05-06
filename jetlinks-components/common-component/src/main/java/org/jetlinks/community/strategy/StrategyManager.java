package org.jetlinks.community.strategy;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 策略管理器接口，用于获取和管理策略对象。
 * 这个接口支持泛型S，它必须是Strategy接口的子类型。
 */
public interface StrategyManager<S extends Strategy> {

    /**
     * 根据提供的标识符获取单个策略对象。
     *
     * @param provider 策略的提供者标识符，用于查找特定的策略。
     * @return 返回一个Mono对象，包含找到的策略对象。如果找不到，则Mono会以空值完成。
     */
    Mono<S> getStrategy(String provider);

    /**
     * 获取所有可用的策略对象。
     *
     * @return 返回一个Flux对象，它会顺序发出所有可用策略对象。如果没有可用策略，则Flux不会发出任何项。
     */
    Flux<S> getStrategies();

}
