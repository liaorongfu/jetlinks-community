package org.jetlinks.community.logging.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.jetlinks.community.logging.system.SerializableSystemLog;

/**
 * SystemLoggingEvent 类用于封装系统日志事件。
 * 这是一个可序列化的事件类，包含了一个系统日志对象。
 */
@Getter
@Setter
@AllArgsConstructor
public class SystemLoggingEvent {
    // 包含系统日志的具体信息
    SerializableSystemLog log;
}

