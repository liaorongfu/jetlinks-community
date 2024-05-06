package org.jetlinks.community.utils;

import lombok.*;
import org.apache.commons.collections4.CollectionUtils;
import org.jetlinks.core.message.MessageType;

import java.util.*;
import java.util.stream.Collectors;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageTypeMatcher {

    @Getter
    private Set<String> excludes; // 排除集合，包含的消息类型不会被匹配

    @Getter
    private Set<String> includes = new HashSet<>(Collections.singleton("*")); // 包含集合，指定的消息类型会被匹配，默认匹配所有

    /**
     * 设置为true时, 优选判断excludes。如果excludes非空，且消息类型在excludes中，则不匹配；否则，判断includes。
     */
    @Setter
    @Getter
    private boolean excludeFirst = true;

    private long excludesMask; // 用于快速判断消息是否在排除集合中的位掩码

    private long includesMask; // 用于快速判断消息是否在包含集合中的位掩码

    /**
     * 设置排除集合。
     * @param excludes 要排除的消息类型的集合
     */
    public void setExcludes(Set<String> excludes) {
        this.excludes = excludes;
        init();
    }

    /**
     * 设置包含集合。
     * @param includes 要包含的消息类型的集合
     */
    public void setIncludes(Set<String> includes) {
        this.includes = includes;
        init();
    }

    /**
     * 根据消息类型集合创建位掩码。
     * @param messageTypes 消息类型集合
     * @return 生成的位掩码
     */
    private long createMask(Collection<MessageType> messageTypes) {
        long mask = 0;

        for (MessageType messageType : messageTypes) {
            mask |= 1L << messageType.ordinal();
        }
        return mask;
    }

    /**
     * 初始化excludesMask和includesMask。
     */
    protected void init() {
        // 根据excludes设置excludesMask
        if (!CollectionUtils.isEmpty(excludes)) {
            if (excludes.contains("*")) {
                excludesMask = createMask(Arrays.asList(MessageType.values()));
            } else {
                excludesMask = createMask(excludes.stream()
                    .map(String::toUpperCase)
                    .map(MessageType::valueOf)
                    .collect(Collectors.toList()));
            }
        }
        // 根据includes设置includesMask
        if (!CollectionUtils.isEmpty(includes)) {
            if (includes.contains("*")) {
                includesMask = createMask(Arrays.asList(MessageType.values()));
            } else {
                includesMask = createMask(includes.stream()
                    .map(String::toUpperCase)
                    .map(MessageType::valueOf)
                    .collect(Collectors.toList()));
            }
        }
    }

    /**
     * 判断消息类型是否匹配。
     * @param type 消息类型
     * @return 如果消息类型匹配，则返回true；否则，返回false。
     */
    public boolean match(MessageType type) {
        long mask = 1L << type.ordinal();
        // 优先判断是否在排除集合中
        if (includesMask != 0) {
            boolean include = (includesMask & mask) != 0;

            if (excludeFirst && excludesMask != 0) {
                return include && (excludesMask & mask) == 0;
            }

            return include;
        }
        // 判断是否在包含集合中
        if (excludesMask != 0) {
            return (excludesMask & mask) == 0;
        }
        // 默认匹配
        return true;
    }
}