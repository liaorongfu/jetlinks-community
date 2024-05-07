package org.jetlinks.community.gateway;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 订阅信息.支持通配符**(匹配多层目录)和*(匹配单层目录).
 *
 * @author zhouhao
 * @since 1.0
 */
@Getter
@Setter
@EqualsAndHashCode(of = "topic")
public class Subscription {

    private String topic; // 订阅的主题

    /**
     * 构造函数，创建一个新的Subscription实例。
     * 如果传入的topic包含MQTT通配符"#”或“+”，会将其替换为对应的适配形式。
     *
     * @param topic 订阅的主题，可以包含MQTT通配符。
     */
    public Subscription(String topic) {
        // 适配mqtt topic通配符
        if (topic.contains("#") || topic.contains("+")) {
            topic = topic.replace("#", "**").replace("+", "*");
        }
        this.topic = topic;
    }

    /**
     * 静态方法，将传入的多个订阅主题转换为Subscription对象的集合。
     *
     * @param sub 可变参数，每个参数代表一个订阅主题。
     * @return 返回一个包含所有Subscription对象的集合。
     */
    public static Collection<Subscription> asList(String... sub) {
        return Stream.of(sub)
            .map(Subscription::new) // 将每个字符串转换为Subscription对象
            .collect(Collectors.toList()); // 收集转换后的对象，形成列表
    }

}

