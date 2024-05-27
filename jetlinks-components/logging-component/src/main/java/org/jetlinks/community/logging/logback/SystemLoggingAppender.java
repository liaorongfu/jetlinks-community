package org.jetlinks.community.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.id.IDGenerator;
import org.hswebframework.web.utils.ModuleUtils;
import org.jetlinks.community.logging.system.SerializableSystemLog;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义日志记录器，扩展自UnsynchronizedAppenderBase，用于将日志事件发布为应用事件。
 */
@Slf4j
public class SystemLoggingAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    // 应用事件发布器，用于发布日志事件
    public static ApplicationEventPublisher publisher;

    // 静态上下文信息，提供全局的上下文数据
    public static final Map<String, String> staticContext = new ConcurrentHashMap<>();

    /**
     * 处理并追加日志事件。
     * @param event 日志事件
     */
    @Override
    protected void append(ILoggingEvent event) {
        // 如果未设置发布器，则直接返回
        if (publisher == null) {
            return;
        }

        // 获取调用者信息
        StackTraceElement element = event.getCallerData()[0];
        // 获取异常信息
        IThrowableProxy proxies = event.getThrowableProxy();
        // 格式化日志消息
        String message = event.getFormattedMessage();
        // 处理异常堆栈信息
        String stack = processStackTrace(event, proxies);

        // 尝试组装系统日志信息，并发布为应用事件
        try {
            // 组装代码位置信息
            String gitLocation = codeLocation(element);
            String mavenModule = moduleInfo(element.getClassName());

            // 组装上下文信息
            Map<String, String> context = new HashMap<>(staticContext);
            Map<String, String> mdc = MDC.getCopyOfContextMap();
            if (mdc != null) {
                context.putAll(mdc);
            }

            // 创建系统日志对象并发布
            SerializableSystemLog info = SerializableSystemLog.builder()
                .id(IDGenerator.SNOW_FLAKE_STRING.generate())
                .mavenModule(mavenModule)
                .context(context)
                .name(event.getLoggerName())
                .level(event.getLevel().levelStr)
                .className(element.getClassName())
                .methodName(element.getMethodName())
                .lineNumber(element.getLineNumber())
                .exceptionStack(stack)
                .java(gitLocation)
                .threadName(event.getThreadName())
                .createTime(event.getTimeStamp())
                .message(message)
                .threadId(String.valueOf(Thread.currentThread().getId()))
                .build();
            publisher.publishEvent(info);
        } catch (Exception e) {
            log.error("组装系统日志错误", e);
        }
    }

    /**
     * 处理异常堆栈信息。
     * @param event 日志事件
     * @param proxies 异常代理
     * @return 堆栈信息字符串
     */
    private String processStackTrace(ILoggingEvent event, IThrowableProxy proxies) {
        StringJoiner joiner = new StringJoiner("\n", event.getFormattedMessage() + "\n[", "]");
        Queue<IThrowableProxy> queue = new LinkedList<>();
        queue.add(proxies);
        while (!queue.isEmpty()) {
            IThrowableProxy proxy = queue.poll();
            if(proxy==null){
                break;
            }
            // 处理堆栈信息
            int commonFrames = proxy.getCommonFrames();
            StackTraceElementProxy[] stepArray = proxy.getStackTraceElementProxyArray();
            StringBuilder stringBuilder = new StringBuilder();
            ThrowableProxyUtil.subjoinFirstLine(stringBuilder, proxy);
            joiner.add(stringBuilder);
            for (int i = 0; i < stepArray.length - commonFrames; i++) {
                StringBuilder sb = new StringBuilder();
                sb.append(CoreConstants.TAB);
                ThrowableProxyUtil.subjoinSTEP(sb, stepArray[i]);
                joiner.add(sb);
            }
            // 添加被抑制的异常
            queue.addAll(Arrays.asList(proxy.getSuppressed()));
        }
        return joiner.toString();
    }

    /**
     * 获取代码位置信息。
     * @param element 调用栈元素
     * @return 代码位置字符串
     */
    private String codeLocation(StackTraceElement element) {
        try {
            Class<?> clazz = Class.forName(element.getClassName());
            ModuleUtils.ModuleInfo moduleInfo = ModuleUtils.getModuleByClass(clazz);
            if (moduleInfo.getGitRepository() != null) {
                StringBuilder javaSb = new StringBuilder();
                javaSb.append(moduleInfo.getGitLocation());
                javaSb.append("src/main/java/");
                javaSb.append(ClassUtils.getPackageName(clazz).replace(".", "/"));
                javaSb.append("/");
                javaSb.append(clazz.getSimpleName());
                javaSb.append(".java#L");
                javaSb.append(element.getLineNumber());
                return javaSb.toString();
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    /**
     * 获取模块信息。
     * @param className 类名称
     * @return Maven模块名称
     */
    private String moduleInfo(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            ModuleUtils.ModuleInfo moduleInfo = ModuleUtils.getModuleByClass(clazz);
            return moduleInfo.getArtifactId();
        } catch (Exception ignore) {
        }
        return null;
    }
}

