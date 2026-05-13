package com.baomihuahua.anticipa.config.common.starter.refresher;

import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import com.baomihuahua.anticipa.core.config.RejectedAnalysisConfig;
import com.baomihuahua.anticipa.core.parser.ConfigParserHandler;
import com.baomihuahua.anticipa.spring.base.support.ApplicationContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于模板方法模式抽象动态线程池刷新逻辑
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractDynamicThreadPoolRefresher implements ApplicationRunner {

    protected final BootstrapConfigProperties properties;

    /**
     * 注册配置变更监听器，由子类实现具体逻辑
     *
     * @throws Exception
     */
    protected abstract void registerListener() throws Exception;

    /**
     * 默认空实现，子类可以按需覆盖
     */
    protected void beforeRegister() {
    }

    /**
     * 默认空实现，子类可以按需覆盖
     */
    protected void afterRegister() {
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        beforeRegister();
        registerListener();
        afterRegister();
    }

    /**
     * 解析配置内容并发布线程池配置变更事件。
     * <p>
     * 此方法通常被 Nacos/Apollo 等配置中心的回调线程调用，
     * 如果抛出异常，配置中心 SDK 通常会静默吞没异常而不打印任何日志，
     * 导致配置刷新静默失败。因此必须在此处捕获异常并打印日志。
     * </p>
     * <p>
     * 必须使用 Bindable.of() 创建新实例，不能用 Bindable.ofInstance(properties)。
     * 原因：ofInstance 会在已有 List 元素上执行 in-place 修改，
     * 而 AnticipaRegistry 中 holder.executorProperties 与 properties.executors[i]
     * 是同一个对象引用，导致 in-place 修改后两者同步变化，
     * DynamicThreadPoolRefreshListener 对比时发现"没有差异"而跳过刷新。
     * 使用 Bindable.of() 创建全新对象，确保 remote 和 original 是不同实例。
     * </p>
     */
    public void refreshThreadPoolProperties(String configInfo) {
        try {
            Map<Object, Object> configInfoMap = ConfigParserHandler.getInstance().parseConfig(configInfo, properties.getConfigFileType());
            Map<String, Object> flattenConfigMap = new LinkedHashMap<>();
            flatten("", configInfoMap, flattenConfigMap);
            ConfigurationPropertySource sources = new MapConfigurationPropertySource(flattenConfigMap);
            Binder binder = new Binder(sources);

            // 必须使用 Bindable.of() 创建全新实例。若无 anticipa 前缀键，.get() 会抛异常导致整段刷新失败，
            // 钉钉合并与线程池事件都不会执行，故用 orElse 兜底。
            BootstrapConfigProperties refresherProperties = binder.bind(
                    BootstrapConfigProperties.PREFIX, Bindable.of(BootstrapConfigProperties.class))
                    .orElseGet(BootstrapConfigProperties::new);

            if (refresherProperties.getExecutors() != null) {
                log.info("[DynamicThreadPoolRefresher] After bind: executors count={}, first executor: threadPoolId={}, corePoolSize={}, maximumPoolSize={}",
                        refresherProperties.getExecutors().size(),
                        refresherProperties.getExecutors().isEmpty() ? "N/A" : refresherProperties.getExecutors().get(0).getThreadPoolId(),
                        refresherProperties.getExecutors().isEmpty() ? "N/A" : refresherProperties.getExecutors().get(0).getCorePoolSize(),
                        refresherProperties.getExecutors().isEmpty() ? "N/A" : refresherProperties.getExecutors().get(0).getMaximumPoolSize());
            } else {
                log.warn("[DynamicThreadPoolRefresher] After bind: executors is null!");
            }

            ApplicationContext ctx = ApplicationContextHolder.getInstance();
            if (ctx != null) {
                try {
                    BootstrapConfigProperties liveBootstrap = ctx.getBean(BootstrapConfigProperties.class);
                    syncLiveDingTalkRelatedFromRemote(binder, refresherProperties, liveBootstrap);
                } catch (Exception syncEx) {
                    log.warn("[DynamicThreadPoolRefresher] Failed to sync ding-talk related live properties (non-fatal)", syncEx);
                }
            }

            // 发布线程池配置变更事件，触发所有监听器执行线程池参数对比与刷新操作
            // 当前支持的监听器包括：
            // - {@link com.baomihuahua.anticipa.config.common.starter.refresher.DynamicThreadPoolRefreshListener}
            // - {@link com.baomihuahua.anticipa.web.starter.core.WebThreadPoolRefreshListener}
            ApplicationContextHolder.getInstance().publishEvent(new ThreadPoolConfigUpdateEvent(this, refresherProperties));
        } catch (Exception e) {
            log.error("[DynamicThreadPoolRefresher] Failed to refresh thread pool properties. configInfo length: {}, configFileType: {}",
                    configInfo != null ? configInfo.length() : 0, properties.getConfigFileType(), e);
        }
    }

    /**
     * 将 YAML/Properties 解析后的层级结构扁平化为 Spring Binder 可识别的属性路径。
     * 例如：anticipa.executors[0].thread-pool-id = xxx
     */
    @SuppressWarnings("unchecked")
    private void flatten(String path, Object value, Map<String, Object> target) {
        if (value instanceof Map<?, ?> valueMap) {
            for (Map.Entry<?, ?> entry : valueMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String nextPath = path.isEmpty() ? key : path + "." + key;
                flatten(nextPath, entry.getValue(), target);
            }
            return;
        }
        if (value instanceof List<?> valueList) {
            for (int i = 0; i < valueList.size(); i++) {
                String nextPath = path + "[" + i + "]";
                flatten(nextPath, valueList.get(i), target);
            }
            return;
        }
        target.put(path, value);
    }

    /**
     * 将远程配置中的钉钉 Webhook 相关项合并到当前进程内的配置 Bean，
     * 使 {@link com.baomihuahua.anticipa.core.notification.service.DingTalkMessageService} 下次发送即使用新值。
     */
    private void syncLiveDingTalkRelatedFromRemote(Binder binder,
                                                   BootstrapConfigProperties remoteBootstrap,
                                                   BootstrapConfigProperties liveBootstrap) {
        if (remoteBootstrap.getNotifyPlatforms() != null) {
            BootstrapConfigProperties.NotifyPlatformsConfig src = remoteBootstrap.getNotifyPlatforms();
            // 勿用「仅有对象但 url 为空」覆盖掉本地/已合并的有效 Webhook
            boolean hasUrl = StringUtils.hasText(src.getUrl());
            boolean hasPlatform = StringUtils.hasText(src.getPlatform());
            if (hasUrl || hasPlatform) {
                BootstrapConfigProperties.NotifyPlatformsConfig copy = new BootstrapConfigProperties.NotifyPlatformsConfig();
                copy.setPlatform(hasPlatform ? src.getPlatform()
                        : (liveBootstrap.getNotifyPlatforms() != null ? liveBootstrap.getNotifyPlatforms().getPlatform() : null));
                copy.setUrl(hasUrl ? src.getUrl().trim()
                        : (liveBootstrap.getNotifyPlatforms() != null ? liveBootstrap.getNotifyPlatforms().getUrl() : null));
                liveBootstrap.setNotifyPlatforms(copy);
            }
        }

        // 标准 Nacos 示例为 anticipa.rejected-analysis；若文件根下只有 rejected-analysis（无 anticipa 包一层），需回退前缀
        BindResult<RejectedAnalysisConfig> raBind = binder.bind(
                "anticipa.rejected-analysis", Bindable.of(RejectedAnalysisConfig.class));
        if (!raBind.isBound()) {
            raBind = binder.bind("rejected-analysis", Bindable.of(RejectedAnalysisConfig.class));
        }
        if (raBind.isBound()) {
            RejectedAnalysisConfig remoteRa = raBind.get();
            RejectedAnalysisConfig liveRa = ApplicationContextHolder.getBean(RejectedAnalysisConfig.class);
            mergeRejectedAnalysisDingTalkForHotReload(remoteRa, liveRa);
        } else {
            log.warn("[DynamicThreadPoolRefresher] No bindable rejected-analysis in Nacos snapshot "
                    + "(expect anticipa.rejected-analysis.ding-talk.webhook-url or rejected-analysis.ding-talk.webhook-url)");
        }
    }

    private static void mergeRejectedAnalysisDingTalkForHotReload(RejectedAnalysisConfig remote, RejectedAnalysisConfig live) {
        if (remote.getDingTalk() == null) {
            return;
        }
        RejectedAnalysisConfig.DingTalkConfig r = remote.getDingTalk();
        RejectedAnalysisConfig.DingTalkConfig l = live.getDingTalk();
        if (l == null) {
            live.setDingTalk(r);
            return;
        }
        if (StringUtils.hasText(r.getWebhookUrl())) {
            l.setWebhookUrl(r.getWebhookUrl().trim());
        }
        if (r.getSecret() != null) {
            l.setSecret(r.getSecret().isBlank() ? null : r.getSecret().trim());
        }
    }
}
