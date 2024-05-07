package org.jetlinks.community.elastic.search.parser;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.hswebframework.ezorm.core.param.Term;
import org.jetlinks.community.elastic.search.enums.TermTypeEnum;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 默认的术语类型解析器，实现了TermTypeParser接口。
 * @author bsetfeng
 * @since 1.0
 **/
public class DefaultTermTypeParser implements TermTypeParser {

    /**
     * 使用给定的术语构建查询构建器，并通过提供的函数对查询构建器进行进一步处理。
     *
     * @param termSupplier 提供Term对象的供应商。
     * @param function 将QueryBuider转换为BoolQueryBuilder的函数。
     */
    @Override
    public void process(Supplier<Term> termSupplier, Function<QueryBuilder, BoolQueryBuilder> function) {
        // 应用提供的函数到通过termSupplier获取的Term构建的QueryBuilder上
        function.apply(queryBuilder(termSupplier.get()));
    }

    /**
     * 根据Term的类型创建对应的QueryBuilder对象。
     *
     * @param term 要构建查询构建器的Term对象。
     * @return 对应的QueryBuilder对象，如果Term类型不匹配则返回一个空的BoolQueryBuilder。
     */
    private QueryBuilder queryBuilder(Term term) {
        // 尝试根据Term的类型创建特定的QueryBuilder
        return TermTypeEnum.of(term.getTermType().trim())
                           .map(e -> createQueryBuilder(e,term))
                           .orElse(QueryBuilders.boolQuery());
    }

    /**
     * 根据Term类型和Term对象创建具体的QueryBuilder对象。
     * 如果Term的列名包含"."，则创建一个NestedQueryBuilder，否则根据Term类型处理。
     *
     * @param linkTypeEnum Term的类型枚举。
     * @param term 要创建查询构建器的Term对象。
     * @return 对应的QueryBuilder对象。
     */
    static QueryBuilder createQueryBuilder(TermTypeEnum linkTypeEnum, Term term) {
        // 判断列名是否包含"."，以决定是否创建NestedQueryBuilder
        if (term.getColumn().contains(".")) {
            return new NestedQueryBuilder(term.getColumn().split("[.]")[0], linkTypeEnum.process(term), ScoreMode.Max);
        }
        return linkTypeEnum.process(term);
    }
}

