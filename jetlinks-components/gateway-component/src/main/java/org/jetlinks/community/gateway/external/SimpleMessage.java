package org.jetlinks.community.gateway.external;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SimpleMessage类实现了Message接口，用于传递消息。
 * 该类使用了Lombok的@NoArgsConstructor, @AllArgsConstructor, @Getter, @Setter注解，
 * 以便于创建对象、获取和设置对象属性。
 */
@NoArgsConstructor // 无参数构造方法
@AllArgsConstructor // 带有所有字段的构造方法
@Getter // 为所有属性生成get方法
@Setter // 为所有属性生成set方法
public class SimpleMessage implements Message {

    private String requestId; // 请求ID，用于标识消息请求的唯一性

    private String topic; // 消息主题，标识消息的类型或类别

    private Object payload; // 消息负载，实际传递的消息内容，可以是任意对象

    private Type type; // 消息类型，用于区分不同种类的消息

    private String message; // 消息文本，传递的消息文本内容

}

