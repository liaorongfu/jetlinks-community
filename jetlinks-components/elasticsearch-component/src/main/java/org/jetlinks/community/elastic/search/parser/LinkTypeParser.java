package org.jetlinks.community.elastic.search.parser;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.hswebframework.ezorm.core.param.Term;

import java.util.List;
import java.util.function.Consumer;

/**
 * @version 1.0
 **/
public interface LinkTypeParser {

    /**
     * 处理Term列表的方法。
     *
     * @param terms            代表待处理的Term列表，Term是用于构建查询条件的基本单元。
     * @param consumer         一个Consumer接口实例，用于消费Term列表中的每个元素。Consumer是一个lambda表达式，它可以对每个Term进行操作。
     * @param queryBuilders    一个BoolQueryBuilder的集合，用于构建 Elasticsearch 的查询。在处理每个Term时，可能会根据条件向这个集合中添加新的QueryBuilder。
     */
    void process(List<Term> terms, Consumer<Term> consumer, BoolQueryBuilder queryBuilders);
}

