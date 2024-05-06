package org.jetlinks.community.configure.doc;

import org.hswebframework.web.api.crud.entity.EntityFactory;
import org.hswebframework.web.crud.web.ResponseMessage;
import org.reactivestreams.Publisher;
import org.springdoc.core.ReturnTypeParser;
import org.springdoc.webflux.core.SpringDocWebFluxConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

@Configuration // 表示这是一个配置类
@AutoConfigureBefore(SpringDocWebFluxConfiguration.class) // 表示这个配置类在SpringDocWebFluxConfiguration之前自动配置
public class SpringDocCustomizerConfiguration {

    /**
     * 创建一个自定义的ReturnTypeParser Bean。
     * 这个ReturnTypeParser用于定制API文档中返回类型的表现。
     *
     * @param factory 用于创建ResponseMessage实例的EntityFactory
     * @return 自定义的ReturnTypeParser实例
     */
    @Bean
    public ReturnTypeParser operationCustomizer(EntityFactory factory) {

        // 返回一个ReturnTypeParser的匿名子类实例
        return new ReturnTypeParser() {
            /**
             * 根据方法参数获取返回类型。
             * 这个方法会重写ReturnTypeParser的getReturnType方法，用于定制返回类型的表现。
             *
             * @param methodParameter 方法参数的元数据
             * @return 定制后的返回类型
             */
            @Override
            public Type getReturnType(MethodParameter methodParameter) {
                Type type = ReturnTypeParser.super.getReturnType(methodParameter); // 获取原始的返回类型

                if (type instanceof ParameterizedType) { // 检查类型是否参数化
                    ParameterizedType parameterizedType = ((ParameterizedType) type);
                    Type rawType = parameterizedType.getRawType(); // 获取原始类型
                    if (rawType instanceof Class && Publisher.class.isAssignableFrom(((Class<?>) rawType))) { // 检查是否为流类型（如Flux或Mono）
                        Type actualType = parameterizedType.getActualTypeArguments()[0]; // 获取实际的泛型参数类型

                        if (actualType instanceof ParameterizedType) { // 如果实际类型也是参数化类型，则获取其原始类型
                            actualType = ((ParameterizedType) actualType).getRawType();
                        }
                        if (actualType == ResponseEntity.class || actualType == ResponseMessage.class) { // 如果实际类型为ResponseEntity或自定义的ResponseMessage
                            return type; // 直接返回原始类型
                        }
                        boolean returnList = Flux.class.isAssignableFrom(((Class<?>) rawType)); // 检查实际类型是否为List的Flux

                        // 统一返回ResponseMessage，将实际返回类型封装在ResponseMessage中，如果是List类型，则封装在List<ResponseMessage>中
                        return ResolvableType
                            .forClassWithGenerics(
                                Mono.class,
                                ResolvableType.forClassWithGenerics(
                                   factory.getInstanceType(ResponseMessage.class),
                                    returnList ?
                                        ResolvableType.forClassWithGenerics(
                                            List.class,
                                            ResolvableType.forType(parameterizedType.getActualTypeArguments()[0])
                                        ) :
                                        ResolvableType.forType(parameterizedType.getActualTypeArguments()[0])
                                ))
                            .getType();

                    }
                }

                return type; // 如果不满足条件，返回原始类型
            }
        };
    }
}

