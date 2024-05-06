package org.jetlinks.community.buffer;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

/**
 * BufferProperties 类用于设置和管理缓冲区的属性。
 * 它包含以下几个重要属性:
 * - filePath: 缓冲文件存储目录的路径。
 * - size: 缓冲区的大小，当缓冲区内容超过此大小时，将触发处理逻辑。
 * - timeout: 缓冲数据的超时时间，超过此时间未被处理的数据将作相应处理。
 * - parallelism: 并行度，表示缓冲区支持的最大并行写入线程数。
 * - maxRetryTimes: 最大重试次数，数据处理失败后将重试，超过此次数将被放入死队列。
 */
@Getter
@Setter
public class BufferProperties {
    // 缓冲文件存储目录的路径
    private String filePath;

    // 缓冲区的大小，单位为个数，默认值为1000
    private int size = 1000;

    // 缓冲数据的超时时间，默认为1秒
    private Duration timeout = Duration.ofSeconds(1);

    // 并行度，根据系统可用处理器数量动态设置，默认为至少1个
    private int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());

    // 数据处理的最大重试次数，默认为64次
    private long maxRetryTimes = 64;
}

