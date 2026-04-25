package com.baomihuahua.anticipa.spring.base.support;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * 应用上下文持有器，用于在非 Spring 管理的环境中获取 Spring 容器中的 Bean
 */
public class ApplicationContextHolder implements ApplicationContextAware {

    private static ApplicationContext CONTEXT;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ApplicationContextHolder.CONTEXT = applicationContext;
    }

    /**
     * 根据类型从容器中获取 Bean
     *
     * @param clazz Bean 类型
     * @param <T>   泛型
     * @return 对应类型的 Bean 实例
     */
    public static <T> T getBean(Class<T> clazz) {
        return CONTEXT.getBean(clazz);
    }

    /**
     * 根据名称和类型从容器中获取 Bean
     *
     * @param name  Bean 名称
     * @param clazz Bean 类型
     * @param <T>   泛型
     * @return 对应的 Bean 实例
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return CONTEXT.getBean(name, clazz);
    }

    /**
     * 根据类型获取容器中所有匹配的 Bean
     *
     * @param clazz Bean 类型
     * @param <T>   泛型
     * @return 包含所有匹配 Bean 的 Map，key 为 Bean 名称，value 为实例
     */
    public static <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        return CONTEXT.getBeansOfType(clazz);
    }

    /**
     * 查找指定 Bean 上是否存在特定注解
     *
     * @param beanName       Bean 名称
     * @param annotationType 注解类型
     * @param <A>            注解泛型
     * @return 注解实例，若不存在则返回 null
     */
    public static <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType) {
        return CONTEXT.findAnnotationOnBean(beanName, annotationType);
    }

    /**
     * 发布 Spring 事件
     *
     * @param event Spring 事件
     */
    public static void publishEvent(ApplicationEvent event) {
        CONTEXT.publishEvent(event);
    }

    /**
     * 获取当前的 ApplicationContext 实例
     *
     * @return Spring 应用上下文
     */
    public static ApplicationContext getInstance() {
        return CONTEXT;
    }
}
