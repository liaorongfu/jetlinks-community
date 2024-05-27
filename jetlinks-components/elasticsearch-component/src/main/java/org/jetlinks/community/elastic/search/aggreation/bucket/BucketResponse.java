package org.jetlinks.community.elastic.search.aggreation.bucket;

import lombok.*;

import java.util.List;

/**
 * BucketResponse 类用于表示存储桶响应信息。
 * @author bsetfeng
 * @since 1.0
 **/
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BucketResponse {

    private String name; // 存储桶的名称

    private List<Bucket> buckets; // 存储桶列表，包含多个存储桶的信息
}

