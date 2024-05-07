package org.jetlinks.community.dashboard.web;

import com.alibaba.fastjson.JSON;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.exception.NotFoundException;
import org.jetlinks.community.dashboard.DashboardManager;
import org.jetlinks.community.dashboard.DashboardObject;
import org.jetlinks.community.dashboard.MeasurementParameter;
import org.jetlinks.community.dashboard.MeasurementValue;
import org.jetlinks.community.dashboard.web.request.DashboardMeasurementRequest;
import org.jetlinks.community.dashboard.web.response.DashboardInfo;
import org.jetlinks.community.dashboard.web.response.DashboardMeasurementResponse;
import org.jetlinks.community.dashboard.web.response.MeasurementInfo;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@Resource(id="dashboard",name = "仪表盘")
@Authorize
public class DashboardController {
    // 仪表盘控制器，负责处理与仪表盘相关的HTTP请求

    private final DashboardManager dashboardManager;

    // 构造函数，注入DashboardManager依赖
    public DashboardController(DashboardManager dashboardManager) {
        this.dashboardManager = dashboardManager;
    }

    /**
     * 获取所有仪表盘的定义信息
     *
     * @return 返回一个Flux流，包含所有仪表盘的定义信息
     */
    @GetMapping("/defs")
    @QueryAction
    public Flux<DashboardInfo> getDefinitions() {
        // 从dashboardManager中获取所有仪表盘定义，并转换为DashboardInfo对象的流
        return dashboardManager
            .getDashboards()
            .flatMap(DashboardInfo::of);
    }

    /**
     * 获取指定仪表盘中特定对象的测量定义信息
     *
     * @param dashboard 指定的仪表盘名称
     * @param object 指定的对象名称
     * @return 返回一个Flux流，包含指定对象的测量定义信息
     */
    @GetMapping("/def/{dashboard}/{object}/measurements")
    @QueryAction
    public Flux<MeasurementInfo> getMeasurementDefinitions(@PathVariable String dashboard,
                                                           @PathVariable String object) {
        // 根据仪表盘名称和对象名称查询测量定义，并转换为MeasurementInfo对象的流
        return dashboardManager
            .getDashboard(dashboard)
            .flatMap(dash -> dash.getObject(object))
            .flatMapMany(DashboardObject::getMeasurements)
            .flatMap(MeasurementInfo::of);
    }

    /**
     * 获取指定仪表盘中特定对象和测量的维度数据
     *
     * @param dashboard 指定的仪表盘名称
     * @param object 指定的对象名称
     * @param dimension 指定的维度名称
     * @param measurement 指定的测量名称
     * @param params 查询参数
     * @return 返回一个Flux流，包含指定维度的数据
     */
    @GetMapping(value = "/{dashboard}/{object}/{measurement}/{dimension}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Authorize(merge = false)
    public Flux<Object> getMeasurementValue(@PathVariable String dashboard,
                                                      @PathVariable String object,
                                                      @PathVariable String dimension,
                                                      @PathVariable String measurement,
                                                      @RequestParam Map<String, Object> params) {
        // 根据路径变量查询具体的维度数据，支持实时数据流
        return dashboardManager
            .getDashboard(dashboard)
            .flatMap(dash -> dash.getObject(object))
            .flatMap(obj -> obj.getMeasurement(measurement))
            .flatMap(meas -> meas.getDimension(dimension))
            .switchIfEmpty(Mono.error(() -> new NotFoundException("不支持的仪表盘")))
            .flatMapMany(dim -> dim.getValue(MeasurementParameter.of(params)));
    }

    /**
     * 批量获取仪表盘数据，不支持实时数据
     *
     * @param requests 请求参数的流，包含多个仪表盘数据请求
     * @return 返回一个Flux流，包含多个仪表盘测量的响应信息
     */
    @PostMapping(value = "/_multi")
    @Authorize(merge = false)
    public Flux<DashboardMeasurementResponse> getMultiMeasurementValue(@RequestBody Flux<DashboardMeasurementRequest> requests) {
        // 处理批量数据请求，转换并返回响应信息的流
        return requests.flatMap(request -> dashboardManager
            .getDashboard(request.getDashboard())
            .flatMap(dash -> dash.getObject(request.getObject()))
            .flatMap(obj -> obj.getMeasurement(request.getMeasurement()))
            .flatMap(meas -> meas.getDimension(request.getDimension()))
            .filter(dim -> !dim.isRealTime()) // 过滤掉实时数据请求
            .flatMapMany(dim -> dim.getValue(MeasurementParameter.of(request.getParams())))
            .map(val -> DashboardMeasurementResponse.of(request.getGroup(), val)));
    }

    /**
     * 使用EventSource方式批量获取仪表盘数据，支持实时数据
     *
     * @param requestJson 包含多个仪表盘数据请求的JSON字符串
     * @return 返回一个Flux流，包含多个仪表盘测量的响应信息
     */
    @GetMapping(value = "/_multi", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Authorize(merge = false)
    public Flux<DashboardMeasurementResponse> getMultiMeasurementValue(@RequestParam String requestJson) {
        // 从JSON字符串解析请求，处理并返回实时数据流的响应信息
        return Flux.fromIterable(JSON.parseArray(requestJson, DashboardMeasurementRequest.class))
            .flatMap(request -> dashboardManager
                .getDashboard(request.getDashboard())
                .flatMap(dash -> dash.getObject(request.getObject()))
                .flatMap(obj -> obj.getMeasurement(request.getMeasurement()))
                .flatMap(meas -> meas.getDimension(request.getDimension()))
                .flatMapMany(dim -> dim.getValue(MeasurementParameter.of(request.getParams())))
                .map(val -> DashboardMeasurementResponse.of(request.getGroup(), val)));
    }
}

