package org.jetlinks.community.configure.trace;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.jetlinks.community.configure.cluster.ClusterProperties;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.trace.EventBusSpanExporter;
import org.jetlinks.core.trace.TraceHolder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TraceProperties.class)
// 此配置类定义了跟踪相关的配置，启用基于属性的条件配置，当"trace.enabled"为"true"时，条件成立。
public class TraceConfiguration {

    /**
     * 创建并返回一个TraceWebFilter实例。
     * 该过滤器用于在web请求中添加跟踪功能。
     *
     * @return TraceWebFilter 新的TraceWebFilter实例
     */
    @Bean
    public TraceWebFilter traceWebFilter() {
        return new TraceWebFilter();
    }

    /**
     * 配置并将跟踪信息的处理器推送到事件总线中。
     *
     * @param eventBus 事件总线，用于发送和接收事件
     * @return SpanProcessor 用于将span信息导出到事件总线的处理器
     */
    @Bean
    public SpanProcessor eventBusSpanExporter(EventBus eventBus) {
        return SimpleSpanProcessor.create(
            EventBusSpanExporter.create(eventBus)
        );
    }

    /**
     * 创建并配置OpenTelemetry实例。
     * 该实例用于收集、处理和传播跟踪信息。
     *
     * @param spanProcessors 提供Span处理器的对象提供者
     * @param clusterProperties 集群属性，用于配置服务标识
     * @param traceProperties 跟踪配置属性
     * @return OpenTelemetry 配置好的OpenTelemetry实例
     */
    @Bean
    public OpenTelemetry createTelemetry(ObjectProvider<SpanProcessor> spanProcessors,
                                         ClusterProperties clusterProperties,
                                         TraceProperties traceProperties) {
        // 构建SdkTracerProvider配置，并注册Span处理器
        SdkTracerProviderBuilder sdkTracerProvider = SdkTracerProvider.builder();
        spanProcessors.forEach(sdkTracerProvider::addSpanProcessor);
        traceProperties.buildProcessors().forEach(sdkTracerProvider::addSpanProcessor);

        // 设置资源信息，如服务名称
        SdkTracerProvider tracerProvider = sdkTracerProvider
            .setResource(Resource
                             .builder()
                             .put("service.name", clusterProperties.getId())
                             .build())
            .build();

        // 在关闭应用时关闭tracer提供者
        Runtime.getRuntime().addShutdownHook(new Thread(tracerProvider::close));

        // 构建并配置OpenTelemetrySdk实例
        OpenTelemetrySdk telemetry= OpenTelemetrySdk
            .builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();

        // 设置全局OpenTelemetry实例
        TraceHolder.setup(telemetry);
        try {
            GlobalOpenTelemetry.set(telemetry);
        }catch (Throwable ignore){

        }
        return telemetry;
    }

    /**
     * 创建并返回一个WebClientCustomizer实例，用于在WebClient中添加跟踪过滤器。
     *
     * @param openTelemetry OpenTelemetry实例，用于跟踪
     * @return WebClientCustomizer 用于定制WebClient的实例，添加跟踪功能
     */
    @Bean
    public WebClientCustomizer traceWebClientCustomizer(OpenTelemetry openTelemetry) {
        return builder -> builder
            .filters(filters -> {
                // 确保TraceExchangeFilterFunction已被添加
                if (!filters.contains(TraceExchangeFilterFunction.instance())) {
                    filters.add(TraceExchangeFilterFunction.instance());
                }
            });
    }
}

