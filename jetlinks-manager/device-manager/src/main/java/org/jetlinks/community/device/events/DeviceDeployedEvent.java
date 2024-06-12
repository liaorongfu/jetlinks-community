package org.jetlinks.community.device.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.event.DefaultAsyncEvent;
import org.jetlinks.community.device.entity.DeviceInstanceEntity;

import java.util.List;

/**
 * 设备部署事件类。
 * 该类继承自DefaultAsyncEvent，表示一个异步事件，专门用于表示设备部署的操作事件。
 * 使用@AllArgsConstructor注解来生成一个名为of的静态工厂方法，以便于创建DeviceDeployedEvent的实例。
 * 使用@Getter注解来自动生成获取器方法，以便于访问类的成员变量。
 * @author Rabbuse
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class DeviceDeployedEvent extends DefaultAsyncEvent {

    /**
     * 设备实例列表。
     * 该列表包含了此次部署操作涉及到的所有设备实例。
     * 每个设备实例由DeviceInstanceEntity类表示。
     */
    private final List<DeviceInstanceEntity> devices;

}

