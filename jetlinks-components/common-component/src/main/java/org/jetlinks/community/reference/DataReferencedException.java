package org.jetlinks.community.reference;

import lombok.Getter;
import org.hswebframework.web.exception.I18nSupportException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
/**
 * 用于表示数据被其他数据引用异常的类。当尝试删除或修改一个数据时，如果该数据被其他数据引用，
 * 则会抛出此异常。
 *
 * @param dataType 被引用数据的类型。
 * @param dataId 被引用数据的ID。
 * @param referenceList 引用该数据的所有数据的信息列表。
 */
@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class DataReferencedException extends I18nSupportException {

    private final String dataType; // 被引用数据的类型
    private final String dataId; // 被引用数据的ID

    private final List<DataReferenceInfo> referenceList; // 引用该数据的所有数据的信息列表


    /**
     * 构造函数初始化被引用的数据类型、ID和引用列表。
     *
     * @param dataType 被引用数据的类型。
     * @param dataId 被引用数据的ID。
     * @param referenceList 引用该数据的所有数据的信息列表。
     */
    public DataReferencedException(String dataType,
                                   String dataId,
                                   List<DataReferenceInfo> referenceList) {
        this.dataType = dataType;
        this.dataId = dataId;
        this.referenceList = referenceList;

        super.setI18nCode("error.data.referenced"); // 设置国际化错误码
    }

}

