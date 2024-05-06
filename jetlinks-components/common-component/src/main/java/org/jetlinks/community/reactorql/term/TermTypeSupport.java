package org.jetlinks.community.reactorql.term;

import org.hswebframework.ezorm.core.param.Term;
import org.hswebframework.ezorm.rdb.operator.builder.fragments.SqlFragments;
import org.jetlinks.core.metadata.DataType;

/**
 * TermTypeSupport 接口定义了与术语类型支持相关的方法。
 * 它用于确定是否支持特定的数据类型，并且能够基于给定的列名、值和术语创建 SQL 片段。
 */
public interface TermTypeSupport {

    /**
     * 获取术语类型的标识符。
     *
     * @return 类型标识符，作为字符串返回。
     */
    String getType();

    /**
     * 获取术语类型的名称。
     *
     * @return 类型名称，作为字符串返回。
     */
    String getName();

    /**
     * 判断当前术语类型是否支持给定的数据类型。
     *
     * @param type 需要判断是否支持的数据类型。
     * @return 如果支持，则返回 true；否则返回 false。
     */
    boolean isSupported(DataType type);

    /**
     * 基于给定的列名、值和术语创建 SQL 片段。
     *
     * @param column 列名。
     * @param value 列中的值。
     * @param term 用于构建 SQL 的术语。
     * @return SqlFragments 对象，包含构建好的 SQL 片段。
     */
    SqlFragments createSql(String column, Object value, Term term);

    /**
     * 创建并返回一个 TermType 实例，该实例基于此接口实例的类型和名称。
     *
     * @return TermType 实例，代表了术语的类型和名称。
     */
    default TermType type() {
        return TermType.of(getType(), getName());
    }
}

