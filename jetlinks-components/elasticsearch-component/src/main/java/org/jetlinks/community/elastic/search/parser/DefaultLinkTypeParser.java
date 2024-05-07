package org.jetlinks.community.elastic.search.parser;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.hswebframework.ezorm.core.param.Term;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.function.Consumer;

/**
 * 默认的链接类型解析器，实现了LinkTypeParser接口，用于处理术语的逻辑组合。
 * @author bsetfeng
 * @since 1.0
 **/
@Component
public class DefaultLinkTypeParser implements LinkTypeParser {

    // 用于解析术语类型的解析器
    private final TermTypeParser parser = new DefaultTermTypeParser();


    /**
     * 处理给定术语列表，并根据条件构建查询构建器。
     *
     * @param terms 待处理的术语列表。
     * @param consumer 消费每个术语的消费者接口。
     * @param queryBuilders 用于构建 Elasticsearch 查询的构建器。
     */
    @Override
    public void process(List<Term> terms,
                                    Consumer<Term> consumer,
                                    BoolQueryBuilder queryBuilders) {
        // 如果术语列表为空，则直接返回，不执行任何操作
        if (CollectionUtils.isEmpty(terms)) {
            return;
        }
        // 分组处理术语
        for (TermsHandler.TermGroup group : TermsHandler.groupTerms(terms)) {
            // 对 "或" 类型的术语组进行处理
            if (group.type == Term.Type.or) {
                for (Term groupTerm : group.getTerms()) {
                    // 递归处理 "或" 关系的术语
                    handleOr(queryBuilders, groupTerm, consumer);
                }
            } else {
                // 对 "且" 类型的术语组进行处理
                BoolQueryBuilder andQuery = QueryBuilders.boolQuery();
                for (Term groupTerm : group.getTerms()) {
                    // 递归处理 "且" 关系的术语
                    handleAnd(andQuery, groupTerm, consumer);
                }
                // 如果 "且" 条件不为空，则添加为 should 条件
                if (!CollectionUtils.isEmpty(andQuery.must())){
                    queryBuilders.should(andQuery);
                }
            }
        }
    }

    /**
     * 处理 "或" 关系的术语。
     *
     * @param queryBuilders 用于构建 Elasticsearch 查询的构建器。
     * @param term 当前处理的术语。
     * @param consumer 消费当前术语的接口。
     */
    private void handleOr(BoolQueryBuilder queryBuilders,
                          Term term,
                          Consumer<Term> consumer) {
        // 消费当前术语
        consumer.accept(term);
        // 如果术语没有子术语且有值，则将其作为 should 条件
        if (term.getTerms().isEmpty() && term.getValue() != null) {
            parser.process(() -> term, queryBuilders::should);
        } else if (!term.getTerms().isEmpty()){
            // 如果术语有子术语，则递归处理子术语
            BoolQueryBuilder nextQuery = QueryBuilders.boolQuery();
            process(term.getTerms(), consumer, nextQuery);
            queryBuilders.should(nextQuery);
        }
    }

    /**
     * 处理 "且" 关系的术语。
     *
     * @param queryBuilders 用于构建 Elasticsearch 查询的构建器。
     * @param term 当前处理的术语。
     * @param consumer 消费当前术语的接口。
     */
    private void handleAnd(BoolQueryBuilder queryBuilders,
                           Term term,
                           Consumer<Term> consumer) {
        // 消费当前术语
        consumer.accept(term);
        // 如果术语没有子术语且有值，则将其作为 must 条件
        if (term.getTerms().isEmpty() && term.getValue() != null) {
            parser.process(() -> term, queryBuilders::must);
        } else if (!term.getTerms().isEmpty()){
            // 如果术语有子术语，则递归处理子术语
            BoolQueryBuilder nextQuery = QueryBuilders.boolQuery();
            process(term.getTerms(), consumer, nextQuery);
            queryBuilders.must(nextQuery);
        }
    }

}
