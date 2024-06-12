package org.jetlinks.community.device.measurements;

import lombok.AllArgsConstructor;
import org.jetlinks.community.dashboard.MeasurementDefinition;
import org.jetlinks.core.metadata.Metadata;

/**
 * 表示元数据度量定义的类。
 * 该类通过AllArgsConstructor注解实现了静态工厂方法of，用于创建MetadataMeasurementDefinition的实例。
 * @author Rabbuse
 */
@AllArgsConstructor(staticName = "of")
public class MetadataMeasurementDefinition implements MeasurementDefinition {
    /**
     * 元数据对象，包含度量定义的相关信息。
     */
    Metadata metadata;

    /**
     * 获取度量定义的唯一标识符。
     *
     * @return 元数据的唯一标识符。
     */
    @Override
    public String getId() {
        return metadata.getId();
    }

    /**
     * 获取度量定义的名称。
     *
     * @return 元数据的名称。
     */
    @Override
    public String getName() {
        return metadata.getName();
    }
}

