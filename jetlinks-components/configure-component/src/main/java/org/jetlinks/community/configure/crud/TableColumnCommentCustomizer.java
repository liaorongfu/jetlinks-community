package org.jetlinks.community.configure.crud;

import io.swagger.v3.oas.annotations.media.Schema;
import org.hswebframework.ezorm.rdb.metadata.RDBColumnMetadata;
import org.hswebframework.ezorm.rdb.metadata.RDBTableMetadata;
import org.hswebframework.web.crud.configuration.TableMetadataCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Set;
/**
 * 用于自定义表和列的元数据的组件。
 * 实现了{@link TableMetadataCustomizer}接口，用于在数据库表和列的元数据中添加或修改注释。
 */
@Component
public class TableColumnCommentCustomizer implements TableMetadataCustomizer {
    /**
     * 自定义列的元数据。
     * 如果列的注释为空，则尝试从字段或属性的{@link Schema}注解中获取描述信息，用作列的注释。
     *
     * @param entityType 实体类型
     * @param descriptor 属性描述器
     * @param field      字段
     * @param annotations 字段或属性上的注解集合
     * @param column     列的元数据
     */
    @Override
    public void customColumn(Class<?> entityType,
                             PropertyDescriptor descriptor,
                             Field field,
                             Set<Annotation> annotations,
                             RDBColumnMetadata column) {
        // 如果列的注释为空，尝试从Schema注解中获取描述信息
        if (StringUtils.isEmpty(column.getComment())) {
            annotations
                .stream()
                .filter(Schema.class::isInstance)
                .map(Schema.class::cast)
                .findAny()
                .ifPresent(schema -> column.setComment(schema.description()));
        }
    }
    /**
     * 自定义表的元数据。
     * 如果实体类型上有{@link Schema}注解，则将其描述信息用作表的注释。
     *
     * @param entityType 实体类型
     * @param table      表的元数据
     */
    @Override
    public void customTable(Class<?> entityType, RDBTableMetadata table) {
        // 尝试从实体类型上获取Schema注解，并设置表的注释
        Schema schema = entityType.getAnnotation(Schema.class);
        if (null != schema) {
            table.setComment(schema.description());
        }
    }
}
