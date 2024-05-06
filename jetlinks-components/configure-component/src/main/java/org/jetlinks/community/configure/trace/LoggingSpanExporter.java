package org.jetlinks.community.configure.trace;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.AllArgsConstructor;
import org.jetlinks.core.utils.StringBuilderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * 一个用于记录Span信息的SpanExporter实现类，将Span数据记录到日志中。
 */
@AllArgsConstructor
final class LoggingSpanExporter implements SpanExporter {
    private final Logger logger; // 日志记录器

    /**
     * 创建一个LoggingSpanExporter实例。
     *
     * @param name 日志记录器的名称。
     * @return 返回LoggingSpanExporter实例。
     */
    public static LoggingSpanExporter create(String name) {
        return new LoggingSpanExporter(LoggerFactory.getLogger(name));
    }

    /**
     * 导出给定的Span数据集合到日志中。
     *
     * @param spans Span数据集合。
     * @return 如果操作成功，返回成功的CompletableResultCode。
     */
    @Override
    public CompletableResultCode export(@Nonnull Collection<SpanData> spans) {
        // 如果日志级别不支持Trace，则直接返回成功
        if (!logger.isTraceEnabled()) {
            return CompletableResultCode.ofSuccess();
        }
        // 遍历spans集合，将每个Span数据格式化后记录到日志中
        for (SpanData span : spans) {
            // 使用StringBuilder构建日志信息，提高效率
            String log = StringBuilderUtils.buildString(span, ((data, sb) -> {
                InstrumentationLibraryInfo instrumentationLibraryInfo = data.getInstrumentationLibraryInfo();
                sb.append("'")
                  .append(data.getName())
                  .append("' : ")
                  .append(data.getTraceId())
                  .append(" ")
                  .append(data.getSpanId())
                  .append(" ")
                  .append(data.getKind())
                  .append(" [tracer: ")
                  .append(instrumentationLibraryInfo.getName())
                  .append(":")
                  .append(
                      instrumentationLibraryInfo.getVersion() == null
                          ? ""
                          : instrumentationLibraryInfo.getVersion())
                  .append("] ")
                  .append(data.getAttributes());
            }));

            logger.trace(log);
        }
        return CompletableResultCode.ofSuccess();
    }

    /**
     * 刷新数据。
     *
     * @return 返回操作结果，成功则返回成功的CompletableResultCode。
     */
    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    /**
     * 关闭Exporter，执行刷新操作。
     *
     * @return 返回刷新操作的结果，成功则返回成功的CompletableResultCode。
     */
    @Override
    public CompletableResultCode shutdown() {
        return flush();
    }
}

