package org.jetlinks.community.logging.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.jetlinks.community.logging.access.SerializableAccessLog;

/**
 * AccessLoggingEvent 类用于封装访问日志信息。
 * 这是一个可序列化的类，包含了访问日志的详细信息。
 */
@Getter
@Setter
@AllArgsConstructor
public class AccessLoggingEvent {
    // log 属性用于存储访问日志的详细信息。
    SerializableAccessLog log;
}

