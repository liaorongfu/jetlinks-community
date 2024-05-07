package org.jetlinks.community.dashboard.measurements.sys;

import java.io.Serializable;

/**
 * MonitorInfo接口，用于定义一种监控信息类型，该类型支持序列化，并提供两种操作：添加和除法。
 * @param <T> 限定 MonitorInfo 的泛型，必须是 MonitorInfo 的子类型，以支持泛型方法的递归调用。
 */
public interface MonitorInfo<T extends MonitorInfo<T>> extends Serializable {

    /**
     * 添加两个 MonitorInfo 对象。
     * @param info 要添加的 MonitorInfo 对象。
     * @return 返回添加操作后的 MonitorInfo 对象，实现了可变的“链式编程”效果。
     */
    T add(T info);

    /**
     * 对当前 MonitorInfo 对象进行除法操作。
     * @param num 用于除法操作的数字。
     * @return 返回除法操作后的 MonitorInfo 对象，为链式调用提供便利。
     */
    T division(int num);
}

