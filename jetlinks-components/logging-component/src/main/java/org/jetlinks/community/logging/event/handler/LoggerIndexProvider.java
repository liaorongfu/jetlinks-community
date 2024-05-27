package org.jetlinks.community.logging.event.handler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.community.elastic.search.index.ElasticIndex;

/**
 * LoggerIndexProvider定义了日志索引的枚举类型，实现了ElasticIndex接口。
 * 这个枚举用于识别不同的日志类型，并为它们提供对应的索引名称。
 * @author bsetfeng
 * @since 1.0
 **/
@Getter
@AllArgsConstructor
public enum LoggerIndexProvider implements ElasticIndex {

    // 访问日志索引
    ACCESS("access_logger"),
    // 系统日志索引
    SYSTEM("system_logger");

    // 索引名称
    private String index;
}

