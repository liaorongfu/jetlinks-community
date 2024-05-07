package org.jetlinks.community.elastic.search.index;

/**
 * ElasticIndex 接口定义了获取Elasticsearch索引名称的方法。
 * @version 1.0
 **/
public interface ElasticIndex {

    /**
     * 获取Elasticsearch索引的名称。
     *
     * @return 返回字符串类型的Elasticsearch索引名称。
     */
    String getIndex();
}

