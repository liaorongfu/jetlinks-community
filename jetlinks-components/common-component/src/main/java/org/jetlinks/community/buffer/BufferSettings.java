package org.jetlinks.community.buffer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.community.utils.ErrorUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * BufferSettings类用于配置缓冲区的相关设置。
 * @author zhouhao
 * @since 2.0
 */
@Getter
@AllArgsConstructor
public class BufferSettings {

    // 定义默认的重试错误条件谓词，捕获特定的异常类型。
    private static final Predicate<Throwable> DEFAULT_RETRY_WHEN_ERROR =
        e -> ErrorUtils.hasException(e, IOException.class,
                                     TimeoutException.class,
                                     DataAccessResourceFailureException.class,
                                     CannotCreateTransactionException.class,
                                     QueryTimeoutException.class);

    /**
     * 获取默认的重试错误条件谓词。
     *
     * @return 返回默认的重试错误条件谓词。
     */
    public static Predicate<Throwable> defaultRetryWhenError() {
        return DEFAULT_RETRY_WHEN_ERROR;
    }

    // 文件路径
    private final String filePath;
    // 文件名
    private final String fileName;
    // 定义何时重试的错误条件谓词
    private final Predicate<Throwable> retryWhenError;
    // 缓冲区大小
    private final int bufferSize;
    // 缓冲区超时时间
    private final Duration bufferTimeout;
    // 并行度
    private final int parallelism;
    // 最大重试次数
    private final long maxRetryTimes;

    /**
     * 创建一个具有默认设置的BufferSettings实例。
     *
     * @param filePath 文件路径。
     * @param fileName 文件名。
     * @return 返回配置了默认设置的BufferSettings实例。
     */
    public static BufferSettings create(String filePath, String fileName) {
        return new BufferSettings(
            filePath,
            fileName,
            defaultRetryWhenError(),
            1000,
            Duration.ofSeconds(1),
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
            5);
    }

    /**
     * 根据BufferProperties创建BufferSettings实例。
     *
     * @param properties 缓冲区属性。
     * @return 返回根据BufferProperties配置的BufferSettings实例。
     */
    public static BufferSettings create(BufferProperties properties) {
        return create("buffer.queue", properties);
    }

    /**
     * 根据文件名和BufferProperties创建BufferSettings实例。
     *
     * @param fileName 文件名。
     * @param properties 缓冲区属性。
     * @return 返回配置了指定文件名和BufferProperties的BufferSettings实例。
     */
    public static BufferSettings create(String fileName, BufferProperties properties) {
        return create(properties.getFilePath(), fileName).properties(properties);
    }

    /**
     * 创建一个具有修改缓冲区大小的BufferSettings实例。
     *
     * @param bufferSize 缓冲区大小。
     * @return 返回一个新的BufferSettings实例，仅修改了缓冲区大小。
     */
    public BufferSettings bufferSize(int bufferSize) {
        return new BufferSettings(filePath,
                                  fileName,
                                  retryWhenError,
                                  bufferSize,
                                  bufferTimeout,
                                  parallelism,
                                  maxRetryTimes);
    }

    /**
     * 创建一个具有修改缓冲区超时时间的BufferSettings实例。
     *
     * @param bufferTimeout 缓冲区超时时间。
     * @return 返回一个新的BufferSettings实例，仅修改了缓冲区超时时间。
     */
    public BufferSettings bufferTimeout(Duration bufferTimeout) {
        return new BufferSettings(filePath,
                                  fileName,
                                  retryWhenError,
                                  bufferSize,
                                  bufferTimeout,
                                  parallelism,
                                  maxRetryTimes);
    }

    /**
     * 创建一个具有修改并行度的BufferSettings实例。
     *
     * @param parallelism 并行度。
     * @return 返回一个新的BufferSettings实例，仅修改了并行度。
     */
    public BufferSettings parallelism(int parallelism) {
        return new BufferSettings(filePath,
                                  fileName,
                                  retryWhenError,
                                  bufferSize,
                                  bufferTimeout,
                                  parallelism,
                                  maxRetryTimes);
    }

    /**
     * 创建一个具有修改最大重试次数的BufferSettings实例。
     *
     * @param maxRetryTimes 最大重试次数。
     * @return 返回一个新的BufferSettings实例，仅修改了最大重试次数。
     */
    public BufferSettings maxRetry(int maxRetryTimes) {
        return new BufferSettings(filePath,
                                  fileName,
                                  retryWhenError,
                                  bufferSize,
                                  bufferTimeout,
                                  parallelism,
                                  maxRetryTimes);
    }

    /**
     * 创建一个具有修改重试错误条件谓词的BufferSettings实例。
     *
     * @param retryWhenError 重试错误条件谓词。
     * @return 返回一个新的BufferSettings实例，仅修改了重试 BufferSettings retryWhenError(Predicate<Throwable错误条件谓词。
     */
    public BufferSettings retryWhenError(Predicate<Throwable> retryWhenError) {
        return new BufferSettings(filePath,
                                  fileName,
                                  Objects.requireNonNull(retryWhenError),
                                  bufferSize,
                                  bufferTimeout,
                                  parallelism,
                                  maxRetryTimes);
    }

    /**
     * 根据BufferProperties修改BufferSettings实例的属性。
     *
     * @param properties 缓冲区属性。
     * @return 返回一个新的BufferSettings实例，其属性根据BufferProperties进行了修改。
     */
    public BufferSettings properties(BufferProperties properties) {
        return new BufferSettings(filePath,
                                  fileName,
                                  retryWhenError,
                                  properties.getSize(),
                                  properties.getTimeout(),
                                  properties.getParallelism(),
                                  properties.getMaxRetryTimes());
    }


}

