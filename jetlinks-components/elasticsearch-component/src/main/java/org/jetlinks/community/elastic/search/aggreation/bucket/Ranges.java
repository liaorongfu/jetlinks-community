package org.jetlinks.community.elastic.search.aggreation.bucket;

import lombok.Getter;
import lombok.Setter;

/**
 * Ranges类用于定义一个范围，包含起始和结束值。
 * @author bsetfeng
 * @since 1.0
 **/
@Getter
@Setter
public class Ranges {

    private String key; //范围的键值标识

    private Object form; //范围的起始值

    private Object to; //范围的结束值
}

