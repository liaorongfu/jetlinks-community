package org.jetlinks.community.gateway.external.dashboard;

import org.jetlinks.community.dashboard.DashboardManager;
import org.jetlinks.community.dashboard.MeasurementParameter;
import org.jetlinks.community.gateway.external.Message;
import org.jetlinks.community.gateway.external.SubscribeRequest;
import org.jetlinks.community.gateway.external.SubscriptionProvider;
import org.jetlinks.core.utils.TopicUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 仪表盘订阅提供者，实现了SubscriptionProvider接口，用于处理仪表盘的订阅逻辑。
 */
@Component
public class DashBoardSubscriptionProvider implements SubscriptionProvider {

    // 依赖的仪表盘管理器，用于处理与仪表盘相关的操作。
    private final DashboardManager dashboardManager;

    /**
     * 构造函数，注入DashboardManager。
     *
     * @param dashboardManager 仪表盘管理器
     */
    public DashBoardSubscriptionProvider(DashboardManager dashboardManager) {
        this.dashboardManager = dashboardManager;
    }

    /**
     * 获取提供者的唯一标识符。
     *
     * @return 提供者的唯一标识符字符串
     */
    @Override
    public String id() {
        return "dashboard";
    }

    /**
     * 获取提供者的名称。
     *
     * @return 提供者的名称字符串
     */
    @Override
    public String name() {
        return "仪表盘";
    }

    /**
     * 获取订阅的主题模式。
     *
     * @return 字符串数组，包含一个或多个主题模式
     */
    @Override
    public String[] getTopicPattern() {
        return new String[]{"/dashboard/**"};
    }

    /**
     * 订阅指定的主题，并返回消息流。
     *
     * @param request 订阅请求，包含订阅的主题和其他参数
     * @return 返回一个Flux<Message>，表示消息流。这个消息流会不断地发出与订阅主题相关联的消息。
     */
    @Override
    public Flux<Message> subscribe(SubscribeRequest request) {
        // 延迟创建Flux，直到有订阅发生。这样可以确保只有在实际需要时才执行订阅逻辑。
        return Flux.defer(() -> {
            try {
                // 解析主题中的路径变量，例如从"/dashboard/{dashboard}/{object}/{measurement}/{dimension}"中提取变量值。
                Map<String, String> variables = TopicUtils.getPathVariables(
                    "/dashboard/{dashboard}/{object}/{measurement}/{dimension}", request.getTopic());

                // 通过路径变量获取相应的仪表盘、对象、测量和维度，并订阅其值。
                // 这一系列flatMap操作链式检索并订阅了指定的维度值。
                return dashboardManager.getDashboard(variables.get("dashboard"))
                    .flatMap(dashboard -> dashboard.getObject(variables.get("object")))
                    .flatMap(object -> object.getMeasurement(variables.get("measurement")))
                    .flatMap(measurement -> measurement.getDimension(variables.get("dimension")))
                    .flatMapMany(dimension -> dimension.getValue(MeasurementParameter.of(request.getParameter())))
                    .map(val -> Message.success(request.getId(), request.getTopic(), val)); // 将获取到的值封装成消息。
            } catch (Exception e) {
                // 如果过程中发生异常，返回错误消息。
                // 这里使用Flux.error来产生一个错误的Flux，它会立即触发错误并传递给订阅者。
                return Flux.error(new IllegalArgumentException("topic格式错误,正确格式:/dashboard/{dashboard}/{object}/{measurement}/{dimension}", e));
            }
        });
    }

}

