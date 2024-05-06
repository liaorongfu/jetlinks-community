package org.jetlinks.community.micrometer;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 该接口定义了一个获取MeterRegistry的方法。
 * MeterRegistry是用于计量和监控的注册表，可以用来收集各种指标数据。
 *
 * @param metric 指标名称，用于标识需要获取的计量指标。
 * @param tagKeys 标签键的可变参数，用于进一步区分和筛选指标数据。
 * @return 返回一个MeterRegistry实例，该实例与指定的指标和标签键相关联。
 */
public interface MeterRegistrySupplier {

    MeterRegistry getMeterRegistry(String metric, String... tagKeys);

}

