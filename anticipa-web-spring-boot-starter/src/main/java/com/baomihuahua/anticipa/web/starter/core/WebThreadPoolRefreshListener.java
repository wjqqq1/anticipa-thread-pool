package com.baomihuahua.anticipa.web.starter.core;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import com.baomihuahua.anticipa.config.common.starter.refresher.ThreadPoolConfigUpdateEvent;
import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import com.baomihuahua.anticipa.core.notification.dto.WebThreadPoolConfigChangeDTO;
import com.baomihuahua.anticipa.core.notification.service.NotifierDispatcher;
import com.baomihuahua.anticipa.spring.base.support.ApplicationContextHolder;
import com.baomihuahua.anticipa.web.starter.core.executor.WebThreadPoolService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Web 线程池监听配置中心刷新事件
 */
@RequiredArgsConstructor
public class WebThreadPoolRefreshListener implements ApplicationListener<ThreadPoolConfigUpdateEvent> {

    private final WebThreadPoolService webThreadPoolService;
    private final NotifierDispatcher notifierDispatcher;

    @Override
    public void onApplicationEvent(ThreadPoolConfigUpdateEvent event) {
        BootstrapConfigProperties.WebThreadPoolExecutorConfig webExecutorConfig = event.getBootstrapConfigProperties().getWeb();
        if (Objects.isNull(webExecutorConfig)) {
            return;
        }

        WebThreadPoolBaseMetrics basicMetrics = webThreadPoolService.getBasicMetrics();
        if (!Objects.equals(basicMetrics.getCorePoolSize(), webExecutorConfig.getCorePoolSize())
                || !Objects.equals(basicMetrics.getMaximumPoolSize(), webExecutorConfig.getMaximumPoolSize())
                || !Objects.equals(basicMetrics.getKeepAliveTime(), webExecutorConfig.getKeepAliveTime())) {
            // 变更 Web 线程池配置
            webThreadPoolService.updateThreadPool(BeanUtil.toBean(webExecutorConfig, WebThreadPoolConfig.class));

            // 发送 Web 线程池配置变更通知
            sendWebThreadPoolConfigChangeMessage(basicMetrics, webExecutorConfig);
        }
    }

    @SneakyThrows
    private void sendWebThreadPoolConfigChangeMessage(WebThreadPoolBaseMetrics originalProperties,
                                                      BootstrapConfigProperties.WebThreadPoolExecutorConfig remoteProperties) {
        Environment environment = ApplicationContextHolder.getBean(Environment.class);
        String activeProfile = environment.getProperty("spring.profiles.active", "dev");
        String applicationName = environment.getProperty("spring.application.name");

        Map<String, WebThreadPoolConfigChangeDTO.ChangePair<?>> changes = new HashMap<>();
        changes.put("corePoolSize", new WebThreadPoolConfigChangeDTO.ChangePair<>(originalProperties.getCorePoolSize(), remoteProperties.getCorePoolSize()));
        changes.put("maximumPoolSize", new WebThreadPoolConfigChangeDTO.ChangePair<>(originalProperties.getMaximumPoolSize(), remoteProperties.getMaximumPoolSize()));
        changes.put("keepAliveTime", new WebThreadPoolConfigChangeDTO.ChangePair<>(originalProperties.getKeepAliveTime(), remoteProperties.getKeepAliveTime()));

        WebThreadPoolConfigChangeDTO configChangeDTO = WebThreadPoolConfigChangeDTO.builder()
                .activeProfile(activeProfile)
                .identify(InetAddress.getLocalHost().getHostAddress())
                .applicationName(applicationName)
                .webContainerName(webThreadPoolService.getWebContainerType().getName())
                .receives(remoteProperties.getNotify().getReceives())
                .changes(changes)
                .updateTime(DateUtil.now())
                .build();
        notifierDispatcher.sendWebChangeMessage(configChangeDTO);
    }
}
