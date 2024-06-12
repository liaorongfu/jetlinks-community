package org.jetlinks.community.device.events;

import lombok.AllArgsConstructor;
import lombok.Generated;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.event.DefaultAsyncEvent;
import org.jetlinks.community.device.entity.DeviceInstanceEntity;

import java.util.List;

/**
 * 设备注销事件类。
 * 该类用于表示设备注销的异步事件。当一个或多个设备被注销时，会生成此类的实例。
 * 继承自DefaultAsyncEvent，表示这是一个异步事件对象。
 * 使用@AllArgsConstructor注解以便通过一个静态方法of快速创建实例。
 * 使用@Getter和@Setter注解提供对类中属性的访问器和修改器。
 * @author Rabbuse
 */
@Getter
@Setter
@AllArgsConstructor(staticName = "of")
@Generated
public class DeviceUnregisterEvent extends DefaultAsyncEvent {

    /**
     * 注销的设备列表。
     * 该属性包含一个设备实例实体的列表，每个实体代表一个被注销的设备。
     * 列表中的每个DeviceInstanceEntity对象包含关于设备的详细信息，如设备ID、设备类型等。
     */
    private List<DeviceInstanceEntity> devices;

}

