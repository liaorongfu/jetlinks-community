package org.jetlinks.community.buffer;

/**
 * MemoryUsage 接口定义了获取内存使用情况的方法。
 */
public interface MemoryUsage {

    /**
     * 获取当前内存使用量。
     *
     * @return 当前内存使用量的整型值。具体单位由实现决定，可能是字节或其它单位。
     */
    int usage();

}

