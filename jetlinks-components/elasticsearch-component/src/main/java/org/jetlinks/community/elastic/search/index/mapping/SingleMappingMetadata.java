package org.jetlinks.community.elastic.search.index.mapping;

import lombok.*;
import org.jetlinks.community.elastic.search.enums.ElasticDateFormat;
import org.jetlinks.community.elastic.search.enums.ElasticPropertyType;

/**
 * SingleMappingMetadata 类用于表示单个映射元数据。
 * 它包含了映射名称、格式和类型信息。
 * @author bsetfeng
 * @since 1.0
 **/
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SingleMappingMetadata {

    // 映射的名称
    private String name;

    // 映射的日期格式
    private ElasticDateFormat format;

    // 映射的属性类型
    private ElasticPropertyType type;
}

