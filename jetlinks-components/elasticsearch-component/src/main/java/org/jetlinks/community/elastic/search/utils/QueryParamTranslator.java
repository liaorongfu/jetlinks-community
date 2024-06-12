package org.jetlinks.community.elastic.search.utils;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.hswebframework.ezorm.core.param.QueryParam;
import org.hswebframework.ezorm.core.param.Sort;
import org.hswebframework.ezorm.core.param.Term;
import org.hswebframework.ezorm.core.param.TermType;
import org.jetlinks.community.elastic.search.index.ElasticSearchIndexMetadata;
import org.jetlinks.community.elastic.search.parser.DefaultLinkTypeParser;
import org.jetlinks.community.utils.ConverterUtils;
import org.jetlinks.community.utils.TimeUtils;
import org.jetlinks.core.metadata.Converter;
import org.jetlinks.core.metadata.DataType;
import org.jetlinks.core.metadata.PropertyMetadata;
import org.jetlinks.core.metadata.types.DateTimeType;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author zhouhao
 * @since 1.0
 **/
@Slf4j
public class QueryParamTranslator {

    /**
     * 链接类型解析器的实例，用于解析链接类型的字符串表示到对应的枚举值。
     * 使用静态初始化块来确保在类加载时就初始化该实例，提供全局访问。
     */
    static DefaultLinkTypeParser linkTypeParser = new DefaultLinkTypeParser();

    /**
     * 一个什么都不做的参数转换消费者。
     * 它被定义为一个空操作，用于在不需要进行任何处理的情况下占位。
     */
    static Consumer<Term> doNotingParamConverter = (term -> {
    });

    /**
     * 用于数据类型和术语之间转换的消费者映射。
     * 映射是线程安全的ConcurrentHashMap，允许动态添加或更新转换逻辑。
     * key是数据类型的字符串表示，value是实际的转换逻辑。
     */
    static Map<String, BiConsumer<DataType, Term>> converter = new ConcurrentHashMap<>();

    /**
     * 默认的数据类型转换消费者。
     * 当没有找到特定于数据类型的转换逻辑时，使用该消费者作为默认行为。
     * 目前实现为空操作，但可以根据需要进行扩展。
     */
    static BiConsumer<DataType, Term> defaultDataTypeConverter = (type, term) -> {

    };


    /**
     * 判断一个Term是否可能是列表类型。
     * <p>
     * 本函数通过检查Term的类型来判断它是否属于一系列特定的列表操作符，
     * 这些操作符通常需要一个列表作为参数。如果Term的类型是其中之一，
     * 则返回true，表示这个Term可能是一个列表；否则返回false。
     *
     * @param term 要检查的Term对象
     * @return 如果Term可能是列表类型，则返回true；否则返回false。
     */
    private static boolean maybeList(Term term) {
        // 将Term的类型转换为小写，以便与case语句中的字符串进行匹配
        switch (term.getTermType().toLowerCase()) {
            // 如果Term的类型是"in"、"nin"、"btw"或"nbtw"中的一个，则返回true
            case TermType.in:
            case TermType.nin:
            case TermType.btw:
            case TermType.nbtw:
                return true;
            // 如果Term的类型不属于上述列表操作符，则返回false
        }
        return false;
    }


    /**
     * 判断术语是否属于不需要转换的类型。
     *
     * 本函数用于确定特定的术语类型是否应该被免除转换操作。转换操作可能指的是针对术语的特定处理，
     * 这里具体指的是一些特殊术语类型（如空值、非空值等）不需要进行额外的处理。
     *
     * @param term 待判断的术语对象。
     * @return 如果术语类型属于不需要转换的类型，则返回true；否则返回false。
     */
    private static boolean isDoNotConvertValue(Term term) {
        // 根据术语类型的小写形式进行判断
        switch (term.getTermType().toLowerCase()) {
            // 如果术语类型为null、notnull、empty、nempty中的任意一种，则认为不需要转换
            case TermType.isnull:
            case TermType.notnull:
            case TermType.empty:
            case TermType.nempty:
                return true;
        }
        // 如果术语类型不属于上述任何一种，则返回false，表示需要转换
        return false;
    }



    /**
     * 根据数据类型转换值。
     *
     * 此方法用于根据给定的数据类型转换传入的值。如果数据类型是DateTimeType，则将值转换为日期时间的毫秒数；
     * 如果数据类型实现了Converter接口，则使用该接口的convert方法进行转换；对于其他类型，直接返回原值。
     *
     * @param type 数据类型，用于确定如何转换值。
     * @param val 待转换的值。
     * @return 转换后的值，其类型取决于数据类型。
     */
    private static Object convertValue(DataType type, Object val) {
        // 如果数据类型是DateTimeType，则将值转换为日期时间的毫秒数
        if (type instanceof DateTimeType) {
            return TimeUtils.convertToDate(val).getTime();
        } else if (type instanceof Converter) {
            // 如果数据类型实现了Converter接口，则使用该接口的convert方法进行转换
            return ((Converter<?>) type).convert(val);
        }
        // 对于其他类型，直接返回原值
        return val;
    }


    /**
     * 根据查询参数和Elasticsearch索引元数据创建查询构建器。
     * 此方法主要用于处理查询参数，根据元数据的信息，将参数转换为Elasticsearch可识别的查询条件。
     *
     * @param queryParam 查询参数，包含需要进行查询的条件。
     * @param metadata Elasticsearch索引的元数据，用于提供字段的类型信息等。
     * @return 返回构建好的查询构建器，用于后续的查询操作。
     */
    public static QueryBuilder createQueryBuilder(QueryParam queryParam, ElasticSearchIndexMetadata metadata) {
        // 初始化一个布尔查询构建器，用于组合多个查询条件。
        BoolQueryBuilder queryBuilders = QueryBuilders.boolQuery();
        // 默认的参数转换器，不做任何操作。
        Consumer<Term> paramConverter = doNotingParamConverter;
        // 如果元数据不为空，则使用更复杂的参数转换器。
        if (metadata != null) {
            paramConverter = t -> {
                // 检查列名是否为空或应跳过转换，如果是，则直接返回。
                if (ObjectUtils.isEmpty(t.getColumn()) || isDoNotConvertValue(t)) {
                    return;
                }
                // 通过列名获取属性元数据。
                PropertyMetadata property = metadata.getProperty(t.getColumn());
                // 如果属性存在，则进行类型转换。
                if (null != property) {
                    DataType type = property.getValueType();
                    // 根据字段类型和值的类型尝试进行转换，如果值可能是列表，则使用特殊处理。
                    Object value;
                    if (maybeList(t)) {
                        value = ConverterUtils.tryConvertToList(t.getValue(), v -> convertValue(type, v));
                    } else {
                        value = convertValue(type, t.getValue());
                    }
                    // 如果转换成功，则更新参数的值，否则记录警告日志。
                    if (null != value) {
                        t.setValue(value);
                    } else {
                        log.warn("Can not convert {} to {}", t.getValue(), type.getId());
                    }
                    // 使用对应的类型转换器接受类型和参数，进行进一步的处理。
                    converter.getOrDefault(type.getId(), defaultDataTypeConverter).accept(type, t);
                }
            };
        }
        // 使用链接类型解析器处理查询参数，应用参数转换器，并将构建的查询条件添加到查询构建器中。
        linkTypeParser.process(queryParam.getTerms(), paramConverter, queryBuilders);
        // 返回构建好的查询构建器。
        return queryBuilders;
    }

    /**
     * 根据查询参数和索引元数据转换为SearchSourceBuilder对象。
     * SearchSourceBuilder是Elasticsearch查询DSL的一部分，用于构建查询的具体来源。
     *
     * @param queryParam 查询参数，包含分页信息和排序条件。
     * @param metadata 索引元数据，用于辅助构建查询条件。
     * @return 返回构建好的SearchSourceBuilder对象。
     */
    public static SearchSourceBuilder convertSearchSourceBuilder(QueryParam queryParam, ElasticSearchIndexMetadata metadata) {
        // 初始化SearchSourceBuilder对象
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 如果查询参数包含分页信息，则设置分页参数
        if (queryParam.isPaging()) {
            sourceBuilder.from(queryParam.getPageIndex() * queryParam.getPageSize());
            sourceBuilder.size(queryParam.getPageSize());
        }

        // 遍历排序条件，如果有有效的排序字段和顺序，则添加到SearchSourceBuilder中
        for (Sort sort : queryParam.getSorts()) {
            if (!StringUtils.isEmpty(sort.getName())) {
                sourceBuilder.sort(sort.getName(), SortOrder.fromString(sort.getOrder()));
            }
        }

        // 设置查询条件，通过调用createQueryBuilder方法生成，并添加到SearchSourceBuilder中
        return sourceBuilder.query(createQueryBuilder(queryParam, metadata));
    }


}
