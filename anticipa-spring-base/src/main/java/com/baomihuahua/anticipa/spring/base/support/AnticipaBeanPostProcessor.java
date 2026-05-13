package com.baomihuahua.anticipa.spring.base.support;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ReflectUtil;
import com.baomihuahua.anticipa.core.executor.AnticipaExecutor;
import com.baomihuahua.anticipa.core.executor.AnticipaRegistry;
import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorHolder;
import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorProperties;
import com.baomihuahua.anticipa.core.executor.support.BlockingQueueTypeEnum;
import com.baomihuahua.anticipa.core.executor.support.RejectedPolicyTypeEnum;
import com.baomihuahua.anticipa.core.toolkit.ThreadFactoryBuilder;
import com.baomihuahua.anticipa.core.toolkit.ThreadPoolExecutorBuilder;
import com.baomihuahua.anticipa.spring.base.DynamicThreadPool;
import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 动态线程池后置处理器，扫描 Bean 是否为动态线程池，如果是的话进行属性填充和注册。
 * <p>
 * 同时支持纯配置驱动模式：当 Nacos 配置中心中存在 threadPoolId 但本地没有对应的 @Bean 声明时，
 * 自动创建 AnticipaExecutor 实例并注册到 AnticipaRegistry。
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class AnticipaBeanPostProcessor implements BeanPostProcessor {

    private final BootstrapConfigProperties properties;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AnticipaExecutor) {
            log.info("Detected AnticipaExecutor bean [{}], threadPoolId=[{}], properties.executors size=[{}]",
                    beanName, ((AnticipaExecutor) bean).getThreadPoolId(),
                    properties.getExecutors() != null ? properties.getExecutors().size() : "null");
            // 校验 @DynamicThreadPool 注解是否存在（仅日志提示，不阻止注册）
            try {
                DynamicThreadPool dynamicThreadPool = ApplicationContextHolder.findAnnotationOnBean(beanName, DynamicThreadPool.class);
                if (Objects.isNull(dynamicThreadPool)) {
                    log.warn("Bean [{}] is AnticipaExecutor but @DynamicThreadPool annotation not found via ApplicationContext, will still register.", beanName);
                }
            } catch (Exception ex) {
                log.warn("Failed to check @DynamicThreadPool annotation on bean [{}], will still register: {}", beanName, ex.getMessage());
            }

            AnticipaExecutor anticipaExecutor = (AnticipaExecutor) bean;
            // 从配置中心读取动态线程池配置并对线程池进行赋值
            ThreadPoolExecutorProperties executorProperties = properties.getExecutors()
                    .stream()
                    .filter(each -> Objects.equals(anticipaExecutor.getThreadPoolId(), each.getThreadPoolId()))
                    .findFirst()
                    .orElse(null);

            if (executorProperties != null) {
                overrideLocalThreadPoolConfig(executorProperties, anticipaExecutor);
            } else {
                // 远程配置中没有找到匹配的线程池 ID，使用本地默认配置构建属性
                log.warn("Thread pool id [{}] not found in remote configuration, using local defaults.", anticipaExecutor.getThreadPoolId());
                executorProperties = buildPropertiesFromExecutor(anticipaExecutor);
            }

            // 注册到动态线程池注册器，后续监控和报警从注册器获取线程池实例。同时，参数动态变更需要依赖 ThreadPoolExecutorProperties 比对是否有变更
            AnticipaRegistry.putHolder(anticipaExecutor.getThreadPoolId(), anticipaExecutor, executorProperties);
            log.info("Registered AnticipaExecutor [{}] with threadPoolId=[{}] to AnticipaRegistry. Total holders: {}",
                    beanName, anticipaExecutor.getThreadPoolId(), AnticipaRegistry.getAllHolders().size());
        }

        return bean;
    }

    /**
     * 为 Nacos 远程配置中存在但本地没有 @Bean 声明的线程池自动创建实例。
     * <p>
     * 此方法应在所有 Bean 初始化完成后调用（如 ApplicationRunner 或 @EventListener(ApplicationReadyEvent)），
     * 以确保本地声明的 Bean 已全部注册完毕，只处理"缺失"的线程池。
     * </p>
     */
    public void createMissingPoolsFromRemoteConfig() {
        List<ThreadPoolExecutorProperties> executors = properties.getExecutors();
        if (executors == null || executors.isEmpty()) {
            return;
        }

        for (ThreadPoolExecutorProperties executorProperties : executors) {
            String threadPoolId = executorProperties.getThreadPoolId();
            if (threadPoolId == null || threadPoolId.isBlank()) {
                continue;
            }

            // 检查是否已经被本地 @Bean 注册过了
            ThreadPoolExecutorHolder existingHolder = AnticipaRegistry.getHolder(threadPoolId);
            if (existingHolder != null) {
                log.debug("[Anticipa] Thread pool '{}' already registered, skip auto-creation.", threadPoolId);
                continue;
            }

            // 远程配置有但本地没有，自动创建
            log.info("[Anticipa] Auto-creating thread pool '{}' from remote config: core={}, max={}, queue={}, rejected={}",
                    threadPoolId,
                    executorProperties.getCorePoolSize(),
                    executorProperties.getMaximumPoolSize(),
                    executorProperties.getWorkQueue(),
                    executorProperties.getRejectedHandler());

            try {
                AnticipaExecutor executor = createExecutorFromProperties(executorProperties);
                AnticipaRegistry.putHolder(threadPoolId, executor, executorProperties);
                log.info("[Anticipa] Auto-created and registered thread pool '{}'. Total holders: {}",
                        threadPoolId, AnticipaRegistry.getAllHolders().size());
            } catch (Exception e) {
                log.error("[Anticipa] Failed to auto-create thread pool '{}' from remote config", threadPoolId, e);
            }
        }
    }

    /**
     * 根据 ThreadPoolExecutorProperties 创建 AnticipaExecutor 实例。
     */
    private AnticipaExecutor createExecutorFromProperties(ThreadPoolExecutorProperties props) {
        int corePoolSize = props.getCorePoolSize() != null ? props.getCorePoolSize() : Runtime.getRuntime().availableProcessors();
        int maxPoolSize = props.getMaximumPoolSize() != null ? props.getMaximumPoolSize() : corePoolSize + (corePoolSize >> 1);
        long keepAliveTime = props.getKeepAliveTime() != null ? props.getKeepAliveTime() : 30000L;
        String workQueueName = props.getWorkQueue() != null ? props.getWorkQueue() : BlockingQueueTypeEnum.LINKED_BLOCKING_QUEUE.getName();
        Integer queueCapacity = props.getQueueCapacity() != null ? props.getQueueCapacity() : 4096;
        boolean allowCoreThreadTimeOut = props.getAllowCoreThreadTimeOut() != null ? props.getAllowCoreThreadTimeOut() : false;

        BlockingQueue<Runnable> workQueue = BlockingQueueTypeEnum.createBlockingQueue(workQueueName, queueCapacity);
        ThreadFactory threadFactory = ThreadFactoryBuilder.builder()
                .namePrefix(props.getThreadPoolId() + "_")
                .build();

        // 默认使用 AbortPolicy，后续由 AnticipaExecutor.setRejectedExecutionHandler 包装计数
        RejectedExecutionHandler rejectedHandler;
        if (props.getRejectedHandler() != null) {
            rejectedHandler = RejectedPolicyTypeEnum.createPolicy(props.getRejectedHandler());
        } else {
            rejectedHandler = new java.util.concurrent.ThreadPoolExecutor.AbortPolicy();
        }

        AnticipaExecutor executor = new AnticipaExecutor(
                props.getThreadPoolId(),
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                workQueue,
                threadFactory,
                rejectedHandler,
                0L
        );

        executor.allowCoreThreadTimeOut(allowCoreThreadTimeOut);
        return executor;
    }

    /**
     * 当远程配置中不存在线程池 ID 时，从线程池实例的当前状态构建属性对象，
     * 确保线程池仍可被注册到 AnticipaRegistry 进行监控和动态刷新。
     */
    private ThreadPoolExecutorProperties buildPropertiesFromExecutor(AnticipaExecutor executor) {
        ThreadPoolExecutorProperties props = new ThreadPoolExecutorProperties();
        props.setThreadPoolId(executor.getThreadPoolId());
        props.setCorePoolSize(executor.getCorePoolSize());
        props.setMaximumPoolSize(executor.getMaximumPoolSize());
        props.setKeepAliveTime(executor.getKeepAliveTime(TimeUnit.SECONDS));
        props.setAllowCoreThreadTimeOut(executor.allowsCoreThreadTimeOut());
        return props;
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
