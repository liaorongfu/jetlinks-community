package org.jetlinks.community.gateway.monitor;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * 一个组合设备网关监控器，能够管理多个设备网关监控器，并将操作分发给它们。
 */
class CompositeDeviceGatewayMonitor implements DeviceGatewayMonitor {

    // 存储所有添加进来的设备网关监控器
    private List<DeviceGatewayMonitor> monitors = new ArrayList<>();

    /**
     * 添加一个或多个设备网关监控器。
     *
     * @param monitors 可变参数，代表要添加的设备网关监控器。
     * @return 返回当前CompositeDeviceGatewayMonitor实例，支持链式调用。
     */
    public CompositeDeviceGatewayMonitor add(DeviceGatewayMonitor... monitors) {
        return add(Arrays.asList(monitors));
    }

    /**
     * 添加一个设备网关监控器集合。
     *
     * @param monitors 设备网关监控器的集合。
     * @return 返回当前CompositeDeviceGatewayMonitor实例，支持链式调用。
     */
    public CompositeDeviceGatewayMonitor add(Collection<DeviceGatewayMonitor> monitors) {
        this.monitors.addAll(monitors);
        return this;
    }

    /**
     * 对每个添加的监控器执行给定的操作。
     *
     * @param monitorConsumer 消费型接口，用于操作每个DeviceGatewayMonitor实例。
     */
    protected void doWith(Consumer<DeviceGatewayMonitor> monitorConsumer) {
        monitors.forEach(monitorConsumer);
    }

    // 以下方法覆盖DeviceGatewayMonitor接口的方法，将操作分发给所有添加的监控器。

    /**
     * 设置总连接数。
     * 该方法会调用一个监视器（monitor），并将指定的总连接数传递给它。
     *
     * @param total 连接总数。这是一个长整型值，表示当前系统的总连接数。
     */
    @Override
    public void totalConnection(long total) {
        // 通过一个lambda表达式，将total连接数传递给monitor对象的totalConnection方法
        doWith(monitor -> monitor.totalConnection(total));
    }


    /**
     * 当设备网关连接成功时调用此方法。该方法会执行与设备网关连接相关的操作。
     * 通过调用{@code doWith}方法，传递一个Lambda表达式来具体实现连接后的操作。
     *
     * @see #doWith(java.util.function.Consumer)
     */
    @Override
    public void connected() {
        doWith(DeviceGatewayMonitor::connected);
    }

    /**
     * 当设备网关被拒绝访问时执行的操作。
     * 该方法重写了某个接口的方法（具体接口未显示），以处理设备网关被拒绝的场景。
     * 无参数。
     * 无返回值。
     */
    @Override
    public void rejected() {
        // 通过DeviceGatewayMonitor的rejected方法，执行相应的操作
        doWith(DeviceGatewayMonitor::rejected);
    }

    /**
     * 当设备网关断开连接时执行的操作。
     * 该方法重写了某个接口的disconnected方法，具体接口信息因代码上下文不完整而无法提供。
     * 该方法不接受参数，也不返回任何值。
     */
    @Override
    public void disconnected() {
        // 通过lambda表达式调用DeviceGatewayMonitor的disconnected方法
        doWith(DeviceGatewayMonitor::disconnected);
    }

    /**
     * 当接收到消息时执行相应的处理。
     * 该方法重写了父类或接口中的同名方法，具体行为是通过调用{@code doWith}方法来执行{@code DeviceGatewayMonitor.receivedMessage}。
     * 这个方法不接受参数，也不返回任何值。
     */
    @Override
    public void receivedMessage() {
        // 使用函数式接口的方式调用receivedMessage方法
        doWith(DeviceGatewayMonitor::receivedMessage);
    }

    /**
     * 覆盖了发送消息的方法。此方法通过委托给DeviceGatewayMonitor的sentMessage方法来实现消息的发送。
     * 该方法不接受参数，也不返回任何值。
     */
    @Override
    public void sentMessage() {
        // 通过lambda表达式委托给DeviceGatewayMonitor的sentMessage方法执行消息发送
        doWith(DeviceGatewayMonitor::sentMessage);
    }
}

