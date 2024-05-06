package org.jetlinks.community.codec;

import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;

/**
 * 对象序列化器接口，定义了如何创建用于对象序列化和反序列化的输入输出对象。
 */
public interface ObjectSerializer {

    /**
     * 创建一个对象输入流。
     * @param stream 输入流，用于读取序列化对象。
     * @return 返回一个对象输入流，用于反序列化对象。
     */
    ObjectInput createInput(InputStream stream);

    /**
     * 创建一个对象输出流。
     * @param stream 输出流，用于写入序列化对象。
     * @return 返回一个对象输出流，用于序列化对象。
     */
    ObjectOutput createOutput(OutputStream stream);


}

