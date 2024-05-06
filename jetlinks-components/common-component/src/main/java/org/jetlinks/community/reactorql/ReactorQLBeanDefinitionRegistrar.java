package org.jetlinks.community.reactorql;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.index.CandidateComponentsIndex;
import org.springframework.context.index.CandidateComponentsIndexLoader;
import org.springframework.core.type.AnnotationMetadata;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 该类作为ReactorQL的Bean定义注册器，实现了ImportBeanDefinitionRegistrar接口，
 * 用于在Spring上下文中注册ReactorQL操作的Bean定义。
 */
@Slf4j
public class ReactorQLBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {

    /**
     * 注册Bean定义的方法。它会扫描指定包下所有标注了@ReactorQLOperation注解的接口，
     * 并将它们注册为Spring Bean。
     *
     * @param importingClassMetadata 包含引入类元数据的AnnotationMetadata对象。
     * @param registry Bean定义注册表，用于注册Bean定义。
     * @throws Exception 如果过程中发生异常，则抛出。
     */
    @Override
    @SneakyThrows
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, @Nonnull BeanDefinitionRegistry registry) {
        // 获取EnableReactorQL注解的属性值，主要是包名
        Map<String, Object> attr = importingClassMetadata.getAnnotationAttributes(EnableReactorQL.class.getName());
        if (attr == null) {
            return;
        }
        String[] packages = (String[]) attr.get("value");

        // 加载候选组件索引
        CandidateComponentsIndex index = CandidateComponentsIndexLoader.loadIndex(org.springframework.util.ClassUtils.getDefaultClassLoader());
        if (null == index) {
            return;
        }
        // 通过索引获取所有标注了@ReactorQLOperation注解的类名
        Set<String> path = Arrays.stream(packages)
                                 .flatMap(str -> index
                                     .getCandidateTypes(str, ReactorQLOperation.class.getName())
                                     .stream())
                                 .collect(Collectors.toSet());

        // 遍历所有找到的类名
        for (String className : path) {
            // 加载类并检查是否为接口且标注了@ReactorQLOperation注解
            Class<?> type = org.springframework.util.ClassUtils.forName(className, null);
            if (!type.isInterface() || type.getAnnotation(ReactorQLOperation.class) == null) {
                continue;
            }
            // 创建Bean定义并设置相关属性
            RootBeanDefinition definition = new RootBeanDefinition();
            definition.setTargetType(type);
            definition.setBeanClass(ReactorQLFactoryBean.class);
            definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
            definition.getPropertyValues().add("target", type);
            // 如果Spring上下文中还未包含该Bean定义，则注册
            if (!registry.containsBeanDefinition(type.getName())) {
                log.debug("register ReactorQL Operator {}", type);
                registry.registerBeanDefinition(type.getName(), definition);
            }
        }

    }

}

