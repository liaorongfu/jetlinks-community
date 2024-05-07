package org.jetlinks.community.gateway.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 使用Micrometer进行设备网关监控的实现类。
 * 该类提供了对设备网关连接状态及消息收发的计量和监控。
 */
class MicrometerDeviceGatewayMonitor implements DeviceGatewayMonitor {
    MeterRegistry registry; // 用于注册和管理计量器的Registry
    String id; // 监控项的唯一标识符
    String[] tags; // 用于分类和过滤监控数据的标签

    // 用于存储总连接次数的原子引用
    private AtomicReference<Long> totalRef = new AtomicReference<>(0L);

    /**
     * 构造函数，初始化监控项并注册相关的计量器。
     *
     * @param registry 用于注册和管理计量器的MeterRegistry实例。
     * @param id 监控项的唯一标识符。
     * @param tags 用于分类和过滤监控数据的标签数组。
     */
    public MicrometerDeviceGatewayMonitor(MeterRegistry registry, String id, String[] tags) {
        this.registry = registry;
        this.id = id;
        this.tags = tags;

        // 注册一个Gauge来跟踪总连接次数
        Gauge
            .builder(id, totalRef, AtomicReference::get)
            .tags(tags)
            .tag("target", "connection")
            .register(registry);

        // 初始化设备网关连接相关的计数器
        this.connected = getCounter("connected");
        this.rejected = getCounter("rejected");
        this.disconnected = getCounter("disconnected");
        // 初始化消息收发相关的计数器
        this.sentMessage = getCounter("sent_message");
        this.receivedMessage = getCounter("received_message");
    }

    // 设备网关连接、拒绝、断开连接、接收和发送消息的计数器
    final Counter connected;
    final Counter rejected;
    final Counter disconnected;
    final Counter receivedMessage;
    final Counter sentMessage;

    /**
     * 根据给定名称和标签创建并注册一个计数器。
     *
     * @param target 计数器的目标名称。
     * @return 创建并注册后的Counter实例。
     */
    private Counter getCounter(String target) {
        return Counter
            .builder(id)
            .tags(tags)
            .tag("target", target)
            .register(registry);
    }

    /**
     * 更新总连接次数。
     *
     * @param total 新的总连接次数。
     */
    @Override
    public void totalConnection(long total) {
        totalRef.set(Math.max(0, total)); // 保证连接次数非负
    }

    /**
     * 记录一次设备连接事件。
     */
    @Override
    public void connected() {
        connected.increment();
    }

    /**
     * 记录一次设备连接被拒绝事件。
     */
    @Override
    public void rejected() {
        rejected.increment();
    }

    /**
     * 记录一次设备断开连接事件。
     */
    @Override
    public void disconnected() {
        disconnected.increment();
    }

    /**
     * 记录一次接收到消息的事件。
     */
    @Override
    public void receivedMessage() {
        receivedMessage.increment();
    }

    /**
     * 记录一次发送消息的事件。
     */
    @Override
    public void sentMessage() {
        sentMessage.increment();
    }
}

