package com.baomihuahua.anticipa.config.nacos.cloud.starter.refresher;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.baomihuahua.anticipa.config.common.starter.refresher.AbstractDynamicThreadPoolRefresher;
import com.baomihuahua.anticipa.core.executor.support.BlockingQueueTypeEnum;
import com.baomihuahua.anticipa.core.toolkit.ThreadPoolExecutorBuilder;
import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Nacos Cloud 版本刷新处理器
 * <p>
 * 通过注册 Nacos ConfigService 监听器，在配置变化时直接刷新线程池参数。
 * </p>
 */
@Slf4j(topic = "AnticipaConfigRefresher")
public class NacosCloudRefresherHandler extends AbstractDynamicThreadPoolRefresher {

    private ConfigService configService;

    public NacosCloudRefresherHandler(ConfigService configService, BootstrapConfigProperties properties) {
        super(properties);
        this.configService = configService;
    }

    public void registerListener() throws NacosException {
        BootstrapConfigProperties.NacosConfig nacosConfig = properties.getNacos();
        if (nacosConfig == null || nacosConfig.getDataId() == null || nacosConfig.getGroup() == null) {
            log.warn("Nacos config is not configured, skip register listener. Please configure 'anticipa.nacos.data-id' and 'anticipa.nacos.group'.");
            return;
        }
        configService.addListener(
                nacosConfig.getDataId(),
                nacosConfig.getGroup(),
                new Listener() {

                    @Override
                    public Executor getExecutor() {
                        return ThreadPoolExecutorBuilder.builder()
                                .corePoolSize(1)
                                .maximumPoolSize(1)
                                .keepAliveTime(9999L)
                                .workQueueType(BlockingQueueTypeEnum.SYNCHRONOUS_QUEUE)
                                .threadFactory("clod-nacos-refresher-thread_")
                                .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())
                                .build();
                    }

                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        refreshThreadPoolProperties(configInfo);
                    }
                });

        log.info("Dynamic thread pool refresher, add nacos cloud listener success. data-id: {}, group: {}", nacosConfig.getDataId(), nacosConfig.getGroup());

        // 监听器首次 receiveConfigInfo 往往在独立线程异步触发；若仅依赖回调，@Scheduled 等可能在
        // syncLiveDingTalkRelatedFromRemote 执行前就发钉钉，导致 webhook 仍为空。
        try {
            String initial = configService.getConfig(nacosConfig.getDataId(), nacosConfig.getGroup(), 5000L);
            if (initial != null && !initial.isBlank()) {
                log.info("[AnticipaConfigRefresher] Loaded initial Nacos config synchronously (length={}), merging ding-talk / executors",
                        initial.length());
                refreshThreadPoolProperties(initial);
            } else {
                log.warn("[AnticipaConfigRefresher] Nacos getConfig returned empty; anticipa.rejected-analysis.ding-talk 等仍以本地 Environment 为准，直至下次推送");
            }
        } catch (NacosException e) {
            log.warn("[AnticipaConfigRefresher] Initial Nacos getConfig failed (non-fatal): {}", e.toString());
        }
    }
}
