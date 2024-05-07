package org.jetlinks.community.gateway.monitor;

/**
 * 一个不执行任何操作的设备网关监控器实现类。
 * 该类实现了一个设备网关监控器接口，但所有方法都没有实际的实现逻辑。
 */
class NoneDeviceGatewayMonitor implements DeviceGatewayMonitor {

    /**
     * 当总连接数发生变化时调用。
     *
     * @param total 新的总连接数。
     */
    @Override
    public void totalConnection(long total) {

    }

    /**
     * 当设备连接成功时调用。
     */
    @Override
    public void connected() {

    }

    /**
     * 当设备断开连接时调用。
     */
    @Override
    public void disconnected() {

    }

    /**
     * 当连接被拒绝时调用。
     */
    @Override
    public void rejected() {

    }

    /**
     * 当收到消息时调用。
     */
    @Override
    public void receivedMessage() {

    }

    /**
     * 当发送消息成功时调用。
     */
    @Override
    public void sentMessage() {

    }
}

