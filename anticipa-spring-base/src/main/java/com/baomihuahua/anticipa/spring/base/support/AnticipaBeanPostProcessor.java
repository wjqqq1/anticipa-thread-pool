package com.baomihuahua.anticipa.spring.base.support;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ReflectUtil;
import com.baomihuahua.anticipa.core.executor.AnticipaExecutor;
import com.baomihuahua.anticipa.core.executor.AnticipaRegistry;
import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorProperties;
import com.baomihuahua.anticipa.core.executor.support.BlockingQueueTypeEnum;
import com.baomihuahua.anticipa.core.executor.support.RejectedPolicyTypeEnum;
import com.baomihuahua.anticipa.spring.base.DynamicThreadPool;
import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 动态线程池后置处理器，扫描 Bean 是否为动态线程池，如果是的话进行属性填充和注册
 */
@Slf4j
@RequiredArgsConstructor
public class AnticipaBeanPostProcessor implements BeanPostProcessor {

    private final BootstrapConfigProperties properties;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AnticipaExecutor) {
            DynamicThreadPool dynamicThreadPool;
            try {
                // 通过 IOC 容器扫描 Bean 是否存在动态线程池注解
                dynamicThreadPool = ApplicationContextHolder.findAnnotationOnBean(beanName, DynamicThreadPool.class);
                if (Objects.isNull(dynamicThreadPool)) {
                    return bean;
                }
            } catch (Exception ex) {
                log.error("Failed to create dynamic thread pool in annotation mode.", ex);
                return bean;
            }

            AnticipaExecutor anticipaExecutor = (AnticipaExecutor) bean;
            // 从配置中心读取动态线程池配置并对线程池进行赋值
            ThreadPoolExecutorProperties executorProperties = properties.getExecutors()
                    .stream()
                    .filter(each -> Objects.equals(anticipaExecutor.getThreadPoolId(), each.getThreadPoolId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("The thread pool id does not exist in the configuration."));

            overrideLocalThreadPoolConfig(executorProperties, anticipaExecutor);

            // 注册到动态线程池注册器，后续监控和报警从注册器获取线程池实例。同时，参数动态变更需要依赖 ThreadPoolExecutorProperties 比对是否有边跟
            AnticipaRegistry.putHolder(anticipaExecutor.getThreadPoolId(), anticipaExecutor, executorProperties);
        }

        return bean;
    }

    private void overrideLocalThreadPoolConfig(ThreadPoolExecutorProperties executorProperties, AnticipaExecutor anticipaExecutor) {
        Integer remoteCorePoolSize = executorProperties.getCorePoolSize();
        Integer remoteMaximumPoolSize = executorProperties.getMaximumPoolSize();
        Assert.isTrue(remoteCorePoolSize <= remoteMaximumPoolSize, "remoteCorePoolSize must be smaller than remoteMaximumPoolSize.");

        // 如果不清楚为什么有这段逻辑，可以参考 Hippo4j Issue https://github.com/opengoofy/hippo4j/issues/1063
        int originalMaximumPoolSize = anticipaExecutor.getMaximumPoolSize();
        if (remoteCorePoolSize > originalMaximumPoolSize) {
            anticipaExecutor.setMaximumPoolSize(remoteMaximumPoolSize);
            anticipaExecutor.setCorePoolSize(remoteCorePoolSize);
        } else {
            anticipaExecutor.setCorePoolSize(remoteCorePoolSize);
            anticipaExecutor.setMaximumPoolSize(remoteMaximumPoolSize);
        }

        // 阻塞队列没有常规 set 方法，所以使用反射赋值
        BlockingQueue workQueue = BlockingQueueTypeEnum.createBlockingQueue(executorProperties.getWorkQueue(), executorProperties.getQueueCapacity());
        // Java 9+ 的模块系统（JPMS）默认禁止通过反射访问 JDK 内部 API 的私有字段，所以需要配置开放反射权限
        // 在启动命令中增加以下参数，显式开放 java.util.concurrent 包
        // IDE 中通过在 VM options 中添加参数：--add-opens=java.base/java.util.concurrent=ALL-UNNAMED
        // 部署的时候，在启动脚本（如 java -jar 命令）中加入该参数：java -jar --add-opens=java.base/java.util.concurrent=ALL-UNNAMED your-app.jar
        ReflectUtil.setFieldValue(anticipaExecutor, "workQueue", workQueue);

        // 赋值动态线程池其他核心参数
        anticipaExecutor.setKeepAliveTime(executorProperties.getKeepAliveTime(), TimeUnit.SECONDS);
        anticipaExecutor.allowCoreThreadTimeOut(executorProperties.getAllowCoreThreadTimeOut());
        anticipaExecutor.setRejectedExecutionHandler(RejectedPolicyTypeEnum.createPolicy(executorProperties.getRejectedHandler()));
    }
}
