package org.jetlinks.community.buffer;

import io.netty.buffer.*;
import io.netty.util.ReferenceCountUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.BasicDataType;
import org.jetlinks.community.codec.Serializers;
import org.jetlinks.core.cache.FileQueue;
import org.jetlinks.core.cache.FileQueueProxy;
import org.jetlinks.core.utils.SerializeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import javax.annotation.Nonnull;
import java.io.*;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 支持持久化的缓存批量操作工具,用于支持数据的批量操作,如批量写入数据到数据库等.
 * <p>
 * 数据将保存在一个文件队列里,如果写入速度跟不上,数据将会尝试写入到本地文件中.
 *
 * <pre>{@code
 *
 *    BufferWriter<Data> writer = BufferWriter
 *    .<Data>create(
 *       "./data/buffer", //文件目录
 *      "my-data.queue", //文件名
 *      buffer->{
 *           return saveData(buffer);
 *      })
 *    .bufferSize(1000)//缓冲大小,当缓冲区超过此数量时将会立即执行写出操作.
 *    .bufferTimeout(Duration.ofSeconds(2))// 缓冲超时时间,当缓冲区超过此时间将会立即执行写出操作.
 *    .parallelism(2); //并行度,表示支持并行写入的线程数.
 *
 *    //开始批量写入数据
 *    writer.start();
 *
 *    //写入缓冲区
 *    writer.write(data);
 * }</pre>
 *
 * @param <T> 数据类型,需要实现Serializable接口
 * @author zhouhao
 * @since pro 2.0
 */
public class PersistenceBuffer<T extends Serializable> implements Disposable {
    // 原子整型更新器，用于更新操作中的wip（工作项数）、remainder（剩余数）和deadSize（死数据数量）
    @SuppressWarnings("all")
    private final static AtomicIntegerFieldUpdater<PersistenceBuffer> WIP =
        AtomicIntegerFieldUpdater.newUpdater(PersistenceBuffer.class, "wip");

    @SuppressWarnings("all")
    private final static AtomicIntegerFieldUpdater<PersistenceBuffer> REMAINDER =
        AtomicIntegerFieldUpdater.newUpdater(PersistenceBuffer.class, "remainder");

    @SuppressWarnings("all")
    private final static AtomicIntegerFieldUpdater<PersistenceBuffer> DEAD_SZIE =
        AtomicIntegerFieldUpdater.newUpdater(PersistenceBuffer.class, "deadSize");
    // 原子引用更新器，用于更新buffer和disposed状态
    @SuppressWarnings("all")
    private final static AtomicReferenceFieldUpdater<PersistenceBuffer, Collection> BUFFER =
        AtomicReferenceFieldUpdater.newUpdater(PersistenceBuffer.class, Collection.class, "buffer");

    @SuppressWarnings("all")
    private final static AtomicReferenceFieldUpdater<PersistenceBuffer, Boolean> DISPOSED =
        AtomicReferenceFieldUpdater.newUpdater(PersistenceBuffer.class, Boolean.class, "disposed");
    // 日志记录器
    private Logger logger = LoggerFactory.getLogger(PersistenceBuffer.class);
    @Getter
    private String name = "unknown";

    //死队列,存储无法完成操作的数据
    private FileQueue<Buf<T>> queue;

    //死队列,存储无法完成操作的数据
    private FileQueue<Buf<T>> deadQueue;

    //缓冲数据处理器,实际处理缓冲数据的逻辑,比如写入数据库.
    private final Function<Flux<T>, Mono<Boolean>> handler;

    //缓冲区大小,超过此大小将执行 handler 处理逻辑
    private BufferSettings settings;
    //缓冲区
    private volatile Collection<Buf<T>> buffer;

    //反序列化时指定快速实例化
    private final Supplier<Externalizable> instanceBuilder;

    //上一次刷新时间
    private long lastFlushTime;

    //当前正在进行的操作
    private volatile int wip;

    //剩余数量
    private volatile int remainder;

    //死数据数量
    private volatile int deadSize;

    //刷新缓冲区定时任务
    private Disposable intervalFlush;
    // 标记是否已释放资源
    private volatile Boolean disposed = false;
    /**
     * 构造函数，初始化PersistenceBuffer实例。
     *
     * @param filePath    文件路径
     * @param fileName    文件名
     * @param newInstance 实例化新数据的供应商，如果为null，则不进行实例化
     * @param handler     处理缓冲区数据的逻辑
     */
    public PersistenceBuffer(String filePath,
                             String fileName,
                             Supplier<T> newInstance,
                             Function<Flux<T>, Mono<Boolean>> handler) {
        this(BufferSettings.create(filePath, fileName), newInstance, handler);
    }
    /**
     * 构造函数，初始化PersistenceBuffer实例。
     *
     * @param filePath    文件路径
     * @param fileName    文件名
     * @param handler     处理缓冲区数据的逻辑
     */
    public PersistenceBuffer(String filePath,
                             String fileName,
                             Function<Flux<T>, Mono<Boolean>> handler) {
        this(filePath, fileName, null, handler);
    }
    /**
     * 构造函数，初始化PersistenceBuffer实例。
     *
     * @param settings    缓冲区设置
     * @param newInstance 实例化新数据的供应商，如果为null，则不进行实例化
     * @param handler     处理缓冲区数据的逻辑
     */
    public PersistenceBuffer(BufferSettings settings,
                             Supplier<T> newInstance,
                             Function<Flux<T>, Mono<Boolean>> handler) {
        if (newInstance != null) {
            T data = newInstance.get();
            if (data instanceof Externalizable) {
                this.instanceBuilder = () -> (Externalizable) newInstance.get();
            } else {
                this.instanceBuilder = null;
            }
        } else {
            this.instanceBuilder = null;
        }
        this.settings = settings;
        //包装一层,防止apply直接报错导致流中断
        this.handler = list -> Mono
            .defer(() -> handler.apply(list));

    }
    /**
     * 配置缓冲区大小。
     *
     * @param size 缓冲区大小
     * @return PersistenceBuffer实例
     */
    public PersistenceBuffer<T> bufferSize(int size) {
        settings = settings.bufferSize(size);
        return this;
    }
    /**
     * 配置缓冲区超时时间。
     *
     * @param bufferTimeout 缓冲区超时时间
     * @return PersistenceBuffer实例
     */
    public PersistenceBuffer<T> bufferTimeout(Duration bufferTimeout) {
        settings = settings.bufferTimeout(bufferTimeout);
        return this;
    }
    /**
     * 配置并行度。
     *
     * @param parallelism 并行度
     * @return PersistenceBuffer实例
     */
    public PersistenceBuffer<T> parallelism(int parallelism) {
        settings = settings.parallelism(parallelism);
        return this;
    }
    /**
     * 配置最大重试次数。
     *
     * @param maxRetryTimes 最大重试次数
     * @return PersistenceBuffer实例
     */
    public PersistenceBuffer<T> maxRetry(int maxRetryTimes) {
        settings = settings.maxRetry(maxRetryTimes);
        return this;
    }
    /**
     * 配置错误重试的条件。
     *
     * @param predicate 重试的条件
     * @return PersistenceBuffer实例
     */
    public PersistenceBuffer<T> retryWhenError(Predicate<Throwable> predicate) {
        settings = settings.retryWhenError(predicate);
        return this;
    }
    /**
     * 配置设置。
     *
     * @param mapper 设置的映射函数
     * @return PersistenceBuffer实例
     */
    public PersistenceBuffer<T> settings(Function<BufferSettings, BufferSettings> mapper) {
        settings = mapper.apply(settings);
        return this;
    }
    /**
     * 设置名称，用于日志记录器的命名。
     *
     * @param name 缓冲区名称
     * @return PersistenceBuffer实例
     */
    public PersistenceBuffer<T> name(String name) {
        this.name = name;
        logger = LoggerFactory.getLogger(PersistenceBuffer.class.getName() + "." + name);
        return this;
    }
    /**
     * 封装FileQueue，提供清除队列方法。
     *
     * @param queue 原始FileQueue
     * @param <T>   数据类型
     * @return 封装后的FileQueue
     */
    static <T> FileQueue<Buf<T>> wrap(FileQueue<Buf<T>> queue) {
        return new FileQueueProxy<Buf<T>>(queue) {
            @Override
            public void clear() {
                super.flush();
            }
        };
    }

    /**
     * 初始化函数，主要完成以下任务：
     * 1. 根据配置创建数据队列和死队列。
     * 2. 配置缓冲区。
     * 3. 对文件名进行处理，替换掉不合法的字符。
     * 4. 初始化数据队列和死队列的大小。
     */
    private void init() {
        // 获取文件路径和文件名
        String filePath = settings.getFilePath();
        String fileName = settings.getFileName();
        // 构建文件路径
        Path path = Paths.get(filePath);

        // 替换文件名中的非法字符，以确保文件名的合法性
        fileName = fileName.replaceAll("[\\s\\\\/:*?\"<>|]", "_");

        // 创建数据类型对象，用于配置队列元素类型
        BufDataType dataType = new BufDataType();

        // 初始化数据队列，配置队列名称、路径和元素类型
        this.queue = wrap(FileQueue
                              .<Buf<T>>builder()
                              .name(fileName)
                              .path(path)
                              .option("valueType", dataType)
                              .build());
        // 初始化剩余元素数量，等同于队列当前大小
        this.remainder = queue.size();

        // 初始化死队列，用于存放处理失败的数据，配置名称、路径和元素类型
        this.deadQueue = wrap(FileQueue
                                  .<Buf<T>>builder()
                                  .name(fileName + ".dead")
                                  .path(path)
                                  .option("valueType", dataType)
                                  .build());
        // 获取死队列当前大小
        this.deadSize = this.deadQueue.size();

        // 初始化缓冲区
        this.buffer = newBuffer();
    }


    /**
     * 启动方法，用于初始化并开始数据刷新流程。
     * 该方法首先检查是否已经启动了定时刷新任务，如果已经启动，则直接返回。
     * 接着，初始化相关资源，然后立即进行一次数据排空操作。
     * 如果配置了非零的缓冲时间间隔，将会启动一个定时任务，按照配置的时间间隔定期执行数据刷新操作。
     */
    public void start() {
        if (intervalFlush != null) {
            // 已经启动了定时刷新任务，直接返回
            return;
        }

        init(); // 初始化

        drain(); // 立即进行一次数据排空

        if (!settings.getBufferTimeout().isZero()) {
            // 配置了非零的缓冲时间间隔，启动定时刷新任务
            intervalFlush = Flux
                .interval(settings.getBufferTimeout())
                .doOnNext(ignore -> intervalFlush()) // 到时间就执行刷新操作
                .subscribe();
        }


    }

    /**
     * 将给定的缓冲区集合加入到死亡队列中，并更新相关计数器。
     *
     * @param buf 待加入死亡队列的缓冲区集合，其类型参数 T 指定了缓冲区元素的类型。
     *        这个集合中的每个元素都代表一个需要被处理的缓冲区对象。
     * @return 无返回值
     */
    private void dead(Collection<Buf<T>> buf) {
        // 尝试将 buf 中的所有元素添加到 deadQueue 中，并根据添加成功的数量更新 DEAD_SZIE 计数器
        if (deadQueue.addAll(buf)) {
            DEAD_SZIE.addAndGet(this, buf.size());
        }
    }

    /**
     * 将指定的 Buf 对象添加到 deadQueue 队列中，并更新相关计数器。
     *
     * @param buf 要添加到 deadQueue 的 Buf 对象。
     * 该方法没有返回值。
     */
    private void dead(Buf<T> buf) {
        // 尝试将 buf 添加到 deadQueue，如果成功则增加 DEAD_SZIE 计数
        if (deadQueue.add(buf)) {
            DEAD_SZIE.incrementAndGet(this);
        }
    }

    /**
     * 将指定的缓冲区集合重新入队，以便后续重试。
     * 对于每个缓冲区，如果其重试次数未超过设置的最大重试次数，则将其重新加入队列；
     * 若超过最大重试次数，则将其标记为死亡状态。
     *
     * @param buffer 待重新入队的缓冲区集合
     */
    private void requeue(Collection<Buf<T>> buffer) {
        for (Buf<T> buf : buffer) {
            // 尝试增加重试次数，并检查是否超过最大重试次数
            if (++buf.retry >= settings.getMaxRetryTimes()) {
                // 如果超过最大重试次数，则将缓冲区标记为死亡状态
                dead(buf);
            } else {
                // 将缓冲区加入队列，若成功则更新剩余量计数
                if (queue.offer(buf)) {
                    REMAINDER.incrementAndGet(this);
                }
            }
        }
    }

    /**
     * 将给定的数据写入队列中，并触发排水操作。
     * <p>
     * 该方法会首先增加剩余量（REMAINDER）的计数，然后将数据（data）加入队列，最后执行排水操作（drain）。
     * <p>
     * 参数：
     * - data: 需要写入队列的数据对象，类型泛型T。
     * <p>
     * 返回值：无
     */
    private void write(Buf<T> data) {
        // 增加剩余量计数
        REMAINDER.incrementAndGet(this);

        // 将数据加入队列
        queue.offer(data);

        // 执行排水操作
        drain();
    }


    /**
     * 将给定的数据写入到某个目标中。
     * 这个方法通过创建一个新的 Buf 对象来包装数据，然后调用另一个 write 方法来完成实际的写入操作。
     *
     * @param data 要写入的数据，类型为泛型 T。
     * 该参数允许任何类型的对象被写入，提高了函数的泛用性。
     * @see #write(Buf)
     */
    public void write(T data) {
        // 使用实例构建器构建一个新的 Buf 对象来包装数据，并调用 write(Buf) 方法进行写入
        write(new Buf<>(data, instanceBuilder));
    }

    /**
     * 释放资源并清理数据。
     * 该方法确保仅被调用一次，并且在调用后将对象标记为已释放状态。
     * 它会取消任何正在进行的定期刷新任务，将缓冲区中的数据写入队列，并关闭相关队列。
     */
    public void dispose() {
        // 尝试将对象状态从未释放变为已释放，仅当状态为未释放时才执行下面的逻辑
        if (DISPOSED.compareAndSet(this, false, true)) {
            // 如果存在定期刷新任务，取消它
            if (this.intervalFlush != null) {
                this.intervalFlush.dispose();
            }
            // 将当前缓冲区的数据添加到队列中，并清空缓冲区
            queue.addAll(BUFFER.getAndSet(this, newBuffer()));
            // 关闭队列，防止进一步添加数据
            queue.close();
            deadQueue.close();
        }
    }

    /**
     * 检查当前对象是否已被废弃。
     *
     * @return 返回一个布尔值，如果对象已被废弃，则为true；否则为false。
     */
    @Override
    public boolean isDisposed() {
        // 通过DISPOSED原子标记检查当前对象是否已废弃
        return DISPOSED.get(this);
    }

    /**
     * 获取当前对象剩余容量的大小。
     * <p>此方法不接受任何参数。</p>
     *
     * @return 返回当前对象剩余容量的大小。返回值为整型。
     */
    public int size() {
        return remainder;
    }


    /**
     * 定时刷新缓冲区的方法。
     * 该方法会检查距离上次刷新的时间是否已经超过设定的缓冲区超时时间，并且当前正在处理的任务数量是否小于或等于并行度设置。
     * 如果两个条件都满足，则调用刷新方法进行数据刷新。
     */
    private void intervalFlush() {
        // 检查是否达到刷新的时间间隔，并且当前处理任务的数量未超过并行度
        if (System.currentTimeMillis() - lastFlushTime >= settings.getBufferTimeout().toMillis()
            && WIP.get(this) <= settings.getParallelism()) {
            flush(); // 进行数据刷新
        }
    }


    /**
     * 将数据集合刷新到目标处理程序中。此方法会尝试将提供的数据集合（Collection<Buf<T>>）处理并提交给指定的处理程序。
     * 如果处理成功，数据将被标记为可重排队或被标记为死数据（无法处理）。
     *
     * @param c 待刷新的数据集合，不应为空。
     */
    private void flush(Collection<Buf<T>> c) {
        try {
            lastFlushTime = System.currentTimeMillis(); // 记录最后一次刷新的时间
            if (c.isEmpty()) {
                drain(); // 如果集合为空，则尝试排水（处理空闲状态）
                return;
            }

            // 增加操作进行中的计数
            WIP.incrementAndGet(this);

            // 使用提供的处理程序处理数据集合
            handler
                .apply(Flux.fromIterable(c).mapNotNull(buf -> buf.data)) // 将集合转换为Flux流并移除空值
                .subscribe(new BaseSubscriber<Boolean>() { // 订阅处理结果
                    final long startWith = System.currentTimeMillis(); // 记录处理开始时间
                    final int remainder = REMAINDER.get(PersistenceBuffer.this); // 获取剩余量

                    /**
                     * 处理数据成功的情况。如果配置了重排队，将根据处理结果决定是否重排队。
                     * @param doRequeue 表示是否需要重排队的布尔值。
                     */
                    @Override
                    protected void hookOnNext(@Nonnull Boolean doRequeue) {
                        if (logger.isDebugEnabled()) {
                            // 记录调试信息：写入数据、大小、剩余量、是否重排队、处理时间
                            logger.debug("write {} data,size:{},remainder:{},requeue: {}.take up time: {} ms",
                                         name,
                                         c.size(),
                                         remainder,
                                         doRequeue,
                                         System.currentTimeMillis() - startWith);
                        }
                        if (doRequeue) {
                            requeue(c); // 如果需要，重排队
                        }
                    }

                    /**
                     * 处理数据失败的情况。根据错误类型决定是重试还是将数据标记为死数据。
                     * @param err 错误对象。
                     */
                    @Override
                    protected void hookOnError(@Nonnull Throwable err) {
                        if (settings.getRetryWhenError().test(err)) {
                            // 如果错误可重试，记录警告信息并重排队
                            if (logger.isWarnEnabled()) {
                                logger.warn("write {} data failed do retry later,size:{},remainder:{}.use time: {} ms",
                                            name,
                                            c.size(),
                                            remainder,
                                            System.currentTimeMillis() - startWith);
                            }
                            requeue(c);
                        } else {
                            // 如果不可重试，记录错误并标记为死数据
                            if (logger.isWarnEnabled()) {
                                logger.warn("write {} data error,size:{},remainder:{}.use time: {} ms",
                                            name,
                                            c.size(),
                                            remainder,
                                            System.currentTimeMillis() - startWith,
                                            err);
                            }
                            dead(c);
                        }
                    }

                    /**
                     * 处理完成后的清理工作。无论成功还是失败，都会减少WIP计数并尝试排水。
                     * @param type 操作的类型，标识是正常完成还是异常结束。
                     */
                    @Override
                    protected void hookFinally(@Nonnull SignalType type) {
                        // 减少操作进行中的计数
                        WIP.decrementAndGet(PersistenceBuffer.this);
                        drain(); // 尝试再次排水
                    }
                });
        } catch (Throwable e) {
            // 记录捕获到的任何异常
            logger.warn("flush buffer error", e);
        }
    }


    /**
     * 将当前缓冲区的内容刷新到目标位置。
     * 这个方法会首先获取当前对象关联的缓冲区内容，然后将其清空，为后续数据存储做准备。
     * 注意：此方法不接受任何参数，也不返回任何值。
     */
    private void flush() {
        // 使用原子操作获取并设置一个新的缓冲区，以避免并发访问问题
        @SuppressWarnings("all")
        Collection<Buf<T>> c = BUFFER.getAndSet(this, newBuffer());
        // 对获取到的缓冲区内容进行刷新操作
        flush(c);
    }


    /**
     * 创建一个新的缓冲区集合。
     * 该方法不接受参数。
     *
     * @return Collection<Buf<T>> - 返回一个基于设置中指定的缓冲区大小的新ArrayList。
     */
    private Collection<Buf<T>> newBuffer() {
        // 根据设置中的缓冲区大小初始化ArrayList
        return new ArrayList<>(settings.getBufferSize());
    }

    /**
     * 方法drain用于处理队列中的元素，直到队列为空或实例被dispose。
     * 它以非阻塞方式尝试从队列中取出元素并处理，处理速度不超过设置的并行度。
     *
     * 无参数。
     * 无返回值。
     */
    private void drain() {
        // 检查当前操作的进行数量是否小于并行度，若小于则请求处理更多操作
        if (WIP.incrementAndGet(this) <= settings.getParallelism()) {
            int size = settings.getBufferSize(); // 获取缓冲区大小
            for (int i = 0; i < size; i++) {
                // 如果实例已被dispose，则终止循环
                if (isDisposed()) {
                    break;
                }
                // 尝试从队列中取出一个元素
                Buf<T> poll = queue.poll();
                // 如果队列不为空，处理取出的元素
                if (poll != null) {
                    onNext(poll);
                } else {
                    // 如果队列为空，终止循环
                    break;
                }
            }
        }
        // 无论是否完成处理，都减少当前进行操作的数量
        WIP.decrementAndGet(this);
    }


    /**
     * 当接收到新的数据项时，将其添加到缓冲区中。
     * 如果缓冲区即将满，会创建一个新的缓冲区并触发数据刷新操作。
     *
     * @param value 需要添加到缓冲区的数据项，不可为null。
     */
    private void onNext(@Nonnull Buf<T> value) {
        // 减少当前对象的剩余容量
        REMAINDER.decrementAndGet(this);

        Collection<Buf<T>> c;
        boolean flush = false; // 标记是否需要刷新缓冲区

        synchronized (this) {
            // 获取当前的缓冲区
            c = buffer();
            // 如果当前缓冲区即将满，准备刷新
            if (c.size() == settings.getBufferSize() - 1) {
                // 使用CAS操作替换当前缓冲区为新的缓冲区
                BUFFER.compareAndSet(this, c, newBuffer());
                flush = true; // 标记为需要刷新
            }
            // 将新值添加到缓冲区
            c.add(value);
        }
        // 如果需要刷新，则执行刷新操作
        if (flush) {
            flush(c);
        }

    }


    /**
     * 获取与当前对象关联的缓冲区集合。
     * 此方法会从一个内部静态变量BUFFER中获取与当前实例相关联的缓冲区集合。
     * 注意：由于涉及类型转换，因此抑制了未经检查的转换警告。
     *
     * @return 返回一个包含缓冲区对象的集合，这些缓冲区对象类型为Buf<T>。
     */
    @SuppressWarnings("unchecked")
    private Collection<Buf<T>> buffer() {
        return BUFFER.get(this);
    }

    /**
     * 创建一个对象输入流。
     * 这个方法通过调用Serializers的getDefault方法，来获取一个默认的序列化器实例，然后使用这个实例
     * 创建一个对象输入流，用于读取给定的ByteBuf中的数据。
     *
     * @param buffer 输入数据的ByteBuf缓冲区。
     * @return 返回一个配置好的对象输入流，用于读取序列化数据。
     * @throws Exception 如果在创建对象输入流过程中发生任何异常，则会抛出。
     */
    @SneakyThrows
    protected ObjectInput createInput(ByteBuf buffer) {
        // 使用默认序列化器创建对象输入流
        return Serializers.getDefault().createInput(new ByteBufInputStream(buffer, true));
    }

    /**
     * 创建一个对象输出流，用于序列化对象到给定的ByteBuf中。
     *
     * @param buffer ByteBuf对象，序列化数据将被写入这个缓冲区。
     * @return 返回一个对象输出流，该流绑定到提供的ByteBuf以便进行序列化操作。
     * @throws Exception 抛出异常的情况被SneakyThrows注解捕获，因此在函数外部不会显式声明抛出异常。
     */
    @SneakyThrows
    protected ObjectOutput createOutput(ByteBuf buffer) {
        // 使用Serializers的默认实例创建一个输出流，该输出流包装了提供的ByteBuf。
        return Serializers.getDefault().createOutput(new ByteBufOutputStream(buffer));
    }

    /**
     * Buf类是一个泛型类，实现了Externalizable接口，用于存储数据以及提供数据序列化和反序列化的能力。
     * 该类支持通过构造函数传入一个Supplier，用于在反序列化时构建新的实例。
     *
     * @param <T> 数据类型
     */
    @AllArgsConstructor
    public static class Buf<T> implements Externalizable {
        // 用于在反序列化时构建新的Externalizable实例的供应商
        private final Supplier<Externalizable> instanceBuilder;
        // 存储的数据
        private T data;
        // 重试次数
        private int retry = 0;

        /**
         * 一个隐藏的构造函数，用于抛出IllegalAccessException异常。
         */
        @SneakyThrows
        public Buf() {
            throw new IllegalAccessException();
        }

        /**
         * 构造函数，仅接受一个Supplier参数，用于实例化Externalizable对象。
         *
         * @param instanceBuilder 用于构建Externalizable实例的Supplier
         */
        public Buf(Supplier<Externalizable> instanceBuilder) {
            this.instanceBuilder = instanceBuilder;
        }

        /**
         * 构造函数，接受一个数据和一个Supplier参数，用于初始化数据和实例化Externalizable对象。
         *
         * @param data 要存储的数据
         * @param instanceBuilder 用于构建Externalizable实例的Supplier
         */
        public Buf(T data, Supplier<Externalizable> instanceBuilder) {
            this.data = data;
            this.instanceBuilder = instanceBuilder;
        }

        /**
         * 序列化方法，将对象写入到外部存储中。
         *
         * @param out ObjectOutput对象，用于输出数据
         * @throws IOException 当写入过程中发生IO异常时抛出
         */
        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeInt(retry); // 序列化重试次数
            if (instanceBuilder != null) {
                // 如果存在instanceBuilder，则调用data的writeExternal方法进行序列化
                ((Externalizable) data).writeExternal(out);
            } else {
                // 否则，使用SerializeUtils序列化data对象
                SerializeUtils.writeObject(data, out);
            }
        }

        /**
         * 反序列化方法，从外部存储中读取对象。
         *
         * @param in ObjectInput对象，用于输入数据
         * @throws IOException           当读取过程中发生IO异常时抛出
         * @throws ClassNotFoundException 当读取过程中找不到类时抛出
         */
        @Override
        @SuppressWarnings("unchecked")
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            retry = in.readInt(); // 反序列化重试次数
            if (instanceBuilder != null) {
                // 如果存在instanceBuilder，则使用它获取一个Externalizable实例，并调用其readExternal方法进行反序列化
                Externalizable data = instanceBuilder.get();
                data.readExternal(in);
                this.data = (T) data;
            } else {
                // 否则，使用SerializeUtils反序列化data对象
                this.data = (T) SerializeUtils.readObject(in);
            }
        }
    }

    /**
     * 定义一个特定于Buf数据类型的类，继承自BasicDataType，用于处理Buf<T>类型的比较、内存计算、读写操作。
     */
    class BufDataType extends BasicDataType<Buf<T>> {

        /**
         * 比较两个Buf对象的内容。
         *
         * @param a 第一个Buf对象
         * @param b 第二个Buf对象
         * @return 总是返回0，表示不基于内容的比较。
         */
        @Override
        public int compare(Buf<T> a, Buf<T> b) {
            return 0;
        }

        /**
         * 计算给定Buf对象占用的内存大小。
         *
         * @param obj 要计算内存使用的Buf对象
         * @return 返回对象占用的内存大小，单位为字节。
         */
        @Override
        public int getMemory(Buf<T> obj) {
            if (obj.data instanceof MemoryUsage) {
                return ((MemoryUsage) obj.data).usage();
            }
            if (obj.data instanceof String) {
                return ((String) obj.data).length() * 2;
            }
            return 10_000;
        }

        /**
         * 将Buf对象的数据写入给定的写入缓冲区。
         *
         * @param buff 写入缓冲区，用于存放数据。
         * @param data 要写入的Buf对象。
         */
        @Override
        @SneakyThrows
        public void write(WriteBuffer buff, Buf<T> data) {
            ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
            try (ObjectOutput output = createOutput(buffer)) {
                data.writeExternal(output);
                output.flush();
                buff.put(buffer.nioBuffer());
            } finally {
                ReferenceCountUtil.safeRelease(buffer);
            }
        }

        /**
         * 批量将Buf对象的数据写入给定的写入缓冲区。
         *
         * @param buff 写入缓冲区，用于存放数据。
         * @param obj 包含多个Buf对象的数组。
         * @param len Buf对象数组的长度。
         */
        @Override
        @SneakyThrows
        public void write(WriteBuffer buff, Object obj, int len) {
            ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
            try (ObjectOutput output = createOutput(buffer)) {
                for (int i = 0; i < len; i++) {
                    @SuppressWarnings("all")
                    Buf<T> buf = ((Buf<T>) Array.get(obj, i));
                    buf.writeExternal(output);
                }
                output.flush();
                buff.put(buffer.nioBuffer());
            } finally {
                ReferenceCountUtil.safeRelease(buffer);
            }

        }

        /**
         * 从给定的字节缓冲区读取数据，并填充到对象数组中。
         *
         * @param buff 字节缓冲区，包含要读取的数据。
         * @param obj 用于存放读取结果的对象数组。
         * @param len 需要读取的Buf对象的数量。
         */
        @Override
        @SneakyThrows
        public void read(ByteBuffer buff, Object obj, int len) {
            try (ObjectInput input = createInput(Unpooled.wrappedBuffer(buff))) {
                for (int i = 0; i < len; i++) {
                    Buf<T> data = new Buf<>(instanceBuilder);
                    data.readExternal(input);
                    Array.set(obj, i, data);
                }
            }
        }

        /**
         * 从给定的字节缓冲区读取一个Buf对象。
         *
         * @param buff 字节缓冲区，包含要读取的数据。
         * @return 读取后的Buf对象。
         */
        @Override
        @SneakyThrows
        public Buf<T> read(ByteBuffer buff) {
            Buf<T> data = new Buf<>(instanceBuilder);
            try (ObjectInput input = createInput(Unpooled.wrappedBuffer(buff))) {
                data.readExternal(input);
            }
            return data;
        }

        /**
         * 创建一个用于存储数据的Buf对象数组。
         *
         * @param size 数组的大小。
         * @return 返回一个未初始化的Buf对象数组。
         */
        @Override
        public Buf<T>[] createStorage(int size) {
            return new Buf[size];
        }
    }

}
