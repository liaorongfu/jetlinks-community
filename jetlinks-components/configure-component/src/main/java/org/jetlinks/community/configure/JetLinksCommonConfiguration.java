package org.jetlinks.community.configure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * JetLinks通用配置类，用于提供一些全局配置。
 */
@Configuration
public class JetLinksCommonConfiguration {

    /**
     * 创建并返回一个Reactor Scheduler，用于在并行模式下执行任务。
     * 这个Scheduler可以在应用程序中被用作并发控制的工具，例如在Flux或Mono操作中指定线程执行。
     *
     * @return Scheduler 并行Scheduler实例。
     */
    @Bean
    public Scheduler reactorScheduler() {
        // 创建并返回一个并行的Scheduler
        return Schedulers.parallel();
    }

}

