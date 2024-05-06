package org.jetlinks.community.configure.trace;

import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.Getter;
import lombok.Setter;
import org.jetlinks.core.trace.TraceHolder;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@ConfigurationProperties(prefix = "trace")
@Getter
@Setter
// TraceProperties 类用于配置跟踪相关的属性
public class TraceProperties {

    private boolean enabled = true; // 跟踪是否启用的标志

    private Set<String> ignoreSpans; // 忽略的 span 名称集合

    // 记录跟踪信息到 Jaeger
    private Jaeger jaeger;

    // 打印跟踪信息到日志
    private Logging logging = new Logging();

    /**
     * 设置忽略的 span 名称集合。
     * @param ignoreSpans 忽略的 span 名称集合
     */
    public void setIgnoreSpans(Set<String> ignoreSpans) {
        this.ignoreSpans = ignoreSpans;
        // 立即禁用指定的 span
        for (String ignoreSpan : ignoreSpans) {
            TraceHolder.disable(ignoreSpan, "sys-conf");
        }
    }

    /**
     * 启用或禁用跟踪。
     * @param enabled 跟踪是否启用的标志
     */
    public void setEnabled(boolean enabled) {
        // 根据 enabled 标志启用或禁用跟踪
        if (enabled) {
            TraceHolder.enable();
        } else {
            TraceHolder.disable();
        }
        this.enabled = enabled;
    }

    /**
     * 构建 span 处理器列表。
     * @return 返回配置的 span 处理器列表
     */
    public List<SpanProcessor> buildProcessors() {
        List<SpanProcessor> processors = new ArrayList<>();
        // 如果 Jaeger 配置存在且启用，则添加对应的 span 处理器
        if (jaeger != null && jaeger.isEnabled()) {
            processors.add(jaeger.create());
        }
        return processors;
    }

    // 用于配置日志打印的内部类
    @Getter
    @Setter
    public static class Logging {
        private String name = "jetlinks.trace"; // 日志名称

        /**
         * 创建并返回一个日志 span 处理器。
         * @return 返回配置的日志 span 处理器
         */
        public SpanProcessor create() {
            return SimpleSpanProcessor.create(
                LoggingSpanExporter.create(name)
            );
        }
    }

    // 配置 Jaeger 作为 span 的处理器的内部类
    public static class Jaeger extends GrpcProcessor {
        /**
         * 创建并返回一个 Jaeger gRPC span 导出器。
         * @return 返回配置的 Jaeger gRPC span 导出器
         */
        @Override
        protected SpanExporter createExporter() {
            return JaegerGrpcSpanExporter
                .builder()
                .setEndpoint(getEndpoint())
                .setTimeout(getTimeout())
                .build();
        }
    }

    // 配置 gRPC 处理器的基类
    @Getter
    @Setter
    public abstract static class GrpcProcessor extends BatchProcessor {
        private String endpoint; // gRPC 服务端点
        private Duration timeout = Duration.ofSeconds(5); // 请求超时时间

        // 创建并返回一个 span 导出器
        protected abstract SpanExporter createExporter();
    }

    // 配置批量处理 span 的基类
    @Getter
    @Setter
    public abstract static class BatchProcessor {
        private boolean enabled = true; // 批量处理是否启用的标志
        private String endpoint; // 服务端点
        private int maxBatchSize = 2048; // 最大批量大小
        private int maxQueueSize = 512; // 最大队列大小
        private Duration exporterTimeout = Duration.ofSeconds(30); // 导出器超时时间
        private Duration scheduleDelay = Duration.ofMillis(100); // 调度延迟

        // 创建并返回一个批量 span 处理器
        public SpanProcessor create() {
            return BatchSpanProcessor
                .builder(createExporter())
                .setScheduleDelay(100, TimeUnit.MILLISECONDS)
                .setMaxExportBatchSize(maxBatchSize)
                .setMaxQueueSize(maxQueueSize)
                .setExporterTimeout(exporterTimeout)
                .build();
        }
    }
}

