package com.baomihuahua.anticipa.core.parser;

import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import com.baomihuahua.anticipa.core.executor.support.BlockingQueueTypeEnum;
import com.baomihuahua.anticipa.core.executor.support.RejectedPolicyTypeEnum;
import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorProperties;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Yaml 配置文件内容解析器
 */
public class YamlConfigParser extends AbstractConfigParser {

    private static final String EXECUTORS_KEY = "onethread.executors";
    private static final String ENABLE_KEY = "onethread.enable";
    private static final String CONFIG_FILE_TYPE_KEY = "onethread.config-file-type";
    private static final String NOTIFY_PLATFORMS_KEY = "onethread.notify-platforms";

    @Override
    public BootstrapConfigProperties parse(String content) {
        BootstrapConfigProperties bootstrapConfigProperties = new BootstrapConfigProperties();

        Yaml yaml = new Yaml();
        Iterable<Object> objects = yaml.loadAll(content);

        for (Object object : objects) {
            if (object instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) object;
                // 处理扁平化的 YAML 键
                if (map.containsKey(ENABLE_KEY)) {
                    bootstrapConfigProperties.setEnable((Boolean) map.get(ENABLE_KEY));
                }
                if (map.containsKey(CONFIG_FILE_TYPE_KEY)) {
                    String configFileType = (String) map.get(CONFIG_FILE_TYPE_KEY);
                    bootstrapConfigProperties.setConfigFileType(ConfigFileTypeEnum.of(configFileType));
                }
                if (map.containsKey(NOTIFY_PLATFORMS_KEY)) {
                    Map<String, Object> notifyMap = (Map<String, Object>) map.get(NOTIFY_PLATFORMS_KEY);
                    BootstrapConfigProperties.NotifyPlatformsConfig notifyPlatformsConfig = new BootstrapConfigProperties.NotifyPlatformsConfig();
                    notifyPlatformsConfig.setPlatform((String) notifyMap.get("platform"));
                    notifyPlatformsConfig.setUrl((String) notifyMap.get("url"));
                    bootstrapConfigProperties.setNotifyPlatforms(notifyPlatformsConfig);
                }
                if (map.containsKey(EXECUTORS_KEY)) {
                    List<Map<String, Object>> executors = (List<Map<String, Object>>) map.get(EXECUTORS_KEY);
                    for (Map<String, Object> executorMap : executors) {
                        ThreadPoolExecutorProperties properties = new ThreadPoolExecutorProperties();
                        properties.setThreadPoolId((String) executorMap.get("threadPoolId"));
                        properties.setCorePoolSize((Integer) executorMap.get("corePoolSize"));
                        properties.setMaximumPoolSize((Integer) executorMap.get("maximumPoolSize"));
                        properties.setQueueCapacity((Integer) executorMap.get("queueCapacity"));
                        properties.setWorkQueue((String) executorMap.get("workQueue"));
                        properties.setRejectedHandler((String) executorMap.get("rejectedHandler"));
                        properties.setKeepAliveTime((Long) executorMap.get("keepAliveTime"));
                        properties.setAllowCoreThreadTimeOut((Boolean) executorMap.get("allowCoreThreadTimeOut"));

                        // 解析 notify
                        if (executorMap.containsKey("notify")) {
                            Map<String, Object> notifyMap = (Map<String, Object>) executorMap.get("notify");
                            ThreadPoolExecutorProperties.NotifyConfig notifyConfig = new ThreadPoolExecutorProperties.NotifyConfig();
                            notifyConfig.setReceives((String) notifyMap.get("receives"));
                            if (notifyMap.containsKey("interval")) {
                                notifyConfig.setInterval((Integer) notifyMap.get("interval"));
                            }
                            properties.setNotify(notifyConfig);
                        }

                        // 解析 alarm
                        if (executorMap.containsKey("alarm")) {
                            Map<String, Object> alarmMap = (Map<String, Object>) executorMap.get("alarm");
                            ThreadPoolExecutorProperties.AlarmConfig alarmConfig = new ThreadPoolExecutorProperties.AlarmConfig();
                            alarmConfig.setEnable((Boolean) alarmMap.getOrDefault("enable", true));
                            alarmConfig.setQueueThreshold((Integer) alarmMap.getOrDefault("queueThreshold", 80));
                            alarmConfig.setActiveThreshold((Integer) alarmMap.getOrDefault("activeThreshold", 80));
                            properties.setAlarm(alarmConfig);
                        }

                        bootstrapConfigProperties.setExecutors(List.of(properties));
                    }
                }
            }
        }

        return bootstrapConfigProperties;
    }
}
