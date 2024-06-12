package org.jetlinks.community.device.function;

import lombok.AllArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.hswebframework.ezorm.core.Conditional;
import org.hswebframework.ezorm.core.NestConditional;
import org.jetlinks.core.things.relation.ObjectSpec;
import org.jetlinks.core.things.relation.RelatedObject;
import org.jetlinks.core.things.relation.RelationObject;
import org.jetlinks.core.things.relation.RelationSpec;
import org.jetlinks.community.device.entity.DeviceInstanceEntity;
import org.jetlinks.community.relation.RelationManagerHolder;
import org.jetlinks.community.relation.RelationObjectProvider;
import org.jetlinks.community.rule.engine.executor.device.DeviceSelectorProvider;
import org.jetlinks.community.rule.engine.executor.device.DeviceSelectorSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class RelationDeviceSelectorProvider implements DeviceSelectorProvider {
    @Override
    public String getProvider() {
        return "relation";
    }

    @Override
    public String getName() {
        return "按关系";
    }

    /**
     * 应用条件到设备选择器规格上，以创建一个嵌套的条件 Mono 流。
     *
     * 此方法旨在处理设备选择的条件应用，它通过将设备选择器规格解析为特定的条件，
     * 并结合上下文信息，来生成一个嵌套的条件流。这个过程涉及到对设备选择器规格的解析，
     * 将解析结果和预定义的条件关系映射到一个 Mono 流中，最后返回这个处理后的 Mono 流。
     *
     * @param source 设备选择器规格，用于定义设备选择的条件。
     * @param ctx 上下文信息，可能包含影响条件应用的额外信息。
     * @param conditional 嵌套的条件对象，用于表示条件的应用结果。
     * @param <T> 条件类型，必须是 Conditional 接口的实现类，并且能够自我扩展。
     * @return 返回一个 Mono 流，该流表示应用了条件的嵌套条件对象。
     */
    @Override
    public <T extends Conditional<T>> Mono<NestConditional<T>> applyCondition(DeviceSelectorSpec source,
                                                                              Map<String, Object> ctx,
                                                                              NestConditional<T> conditional) {

        // 解析设备选择器规格，并将其映射为字符串表示，同时将选择器的值转换为条件关系对象的流。
        // 这里使用了 Mono 和 Flux 流来处理异步数据操作。
        return this
            .applyCondition(
                source.resolve(ctx).map(String::valueOf), // 解析设备选择器规格并转换为字符串
                Flux.fromIterable(source.getSelectorValues()).map(RelationSpec::of), // 将选择器的值转换为条件关系对象的流
                conditional // 嵌套的条件对象
            );
    }


    /**
     * 根据提供的参数列表和现有条件，应用条件到嵌套条件对象。
     * <p>
     * 此方法旨在处理特定的条件应用逻辑，其中参数列表(args)首先被解析以提取关系规格，
     * 然后这些关系规格与第一个参数代表的设备ID一起，用于进一步应用条件到给定的嵌套条件对象。
     * 这种方法的泛型参数<T>确保了条件对象和关系规格之间的类型安全。
     *
     * @param args 参数列表，其中第一个参数代表设备ID，后续参数描述了设备之间的关系。
     * @param conditional 嵌套条件对象，将应用新条件的对象。
     * @param <T> 条件对象的泛型类型，必须扩展自Conditional<T>。
     * @return 返回一个Mono对象，包含应用了新条件的嵌套条件对象。
     */
    @Override
    public <T extends Conditional<T>> Mono<NestConditional<T>> applyCondition(List<?> args,
                                                                              NestConditional<T> conditional) {
        // 从参数列表中提取关系规格，忽略第一个参数（设备ID）
        //第一个参数 为设备ID,其余参数为关系
        Flux<RelationSpec> relations = Flux
            .fromIterable(args)
            .skip(1) // 跳过第一个参数（设备ID）
            .map(RelationSpec::of); // 将剩余参数映射为关系规格对象

        // 应用条件，包括设备ID和关系规格，到给定的嵌套条件对象
        return applyCondition(Flux.just(String.valueOf(args.get(0))), relations, conditional);
    }


    /**
     * 根据条件应用筛选器。
     * <p>
     * 此方法接收一个字符串流（源设备ID）、关系规范流和一个嵌套条件对象，通过分析源设备ID和关系规范，
     * 来确定目标设备ID列表，并据此更新条件对象，以实现条件筛选的功能。
     *
     * @param source 设备ID的流，代表源设备。
     * @param relations 关系规范的流，描述源设备和目标设备之间的关系。
     * @param conditional 嵌套条件对象，用于存储和表达筛选条件。
     * @param <T> 条件对象的泛型，必须实现Conditional接口。
     * @return 返回更新后的嵌套条件对象。
     */
    public <T extends Conditional<T>> Mono<NestConditional<T>> applyCondition(Flux<String> source,
                                                                              Flux<RelationSpec> relations,
                                                                              NestConditional<T> conditional) {
        // 将源设备ID与关系规范合并处理，以确定目标设备ID。
        return source
            .flatMap(deviceId -> relations
                .flatMap(spec -> {
                    // 创建对象规范，用于查询与设备相关联的对象。
                    // deviceId@device:relation@user
                    ObjectSpec objectSpec = new ObjectSpec();
                    objectSpec.setObjectType(RelationObjectProvider.TYPE_DEVICE);
                    objectSpec.setObjectId(deviceId);
                    objectSpec.setRelated(spec);
                    // 根据对象规范查询相关对象。
                    return RelationManagerHolder
                        .getObjects(objectSpec)
                        .flatMap(obj -> {
                            // 如果查询到的对象类型为设备，则直接返回该对象。
                            // 已经选择到了设备
                            if (obj.getType().equals(RelationObjectProvider.TYPE_DEVICE)) {
                                return Mono.just(obj);
                            }
                            // 如果查询到的对象不是设备，则通过其关系反向查询设备对象。
                            return obj
                                // 反转获取,表示获取与上一个关系相同的设备
                                .relations(true)
                                .get(RelationObjectProvider.TYPE_DEVICE, ((RelatedObject) obj).getRelation());
                        });
                }))
            // 提取设备ID，并收集为列表。
            .map(RelationObject::getId)
            .collectList()
            // 根据设备ID列表的为空情况，更新条件对象。
            .doOnNext(deviceIdList -> {
                if (CollectionUtils.isNotEmpty(deviceIdList)){
                    // 如果设备ID列表不为空，更新条件对象为设备ID在列表中。
                    conditional.in(DeviceInstanceEntity::getId, deviceIdList);
                }else {
                    // 如果设备ID列表为空，更新条件对象为设备ID为空。
                    conditional.isNull(DeviceInstanceEntity::getId);
                }
            })
            // 返回更新后的条件对象。
            .thenReturn(conditional);
    }

}
