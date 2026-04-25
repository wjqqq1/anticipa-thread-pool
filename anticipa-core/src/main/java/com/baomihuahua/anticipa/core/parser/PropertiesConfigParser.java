package com.baomihuahua.anticipa.core.parser;

import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorProperties;
import com.baomihuahua.anticipa.core.executor.support.BlockingQueueTypeEnum;
import com.baomihuahua.anticipa.core.executor.support.RejectedPolicyTypeEnum;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Properties 配置文件内容解析器
 */
public class PropertiesConfigParser extends AbstractConfigParser {

    private static final String PREFIX = "onethread.executors.";

    @Override
    public BootstrapConfigProperties parse(String content) {
        BootstrapConfigProperties bootstrapConfigProperties = new BootstrapConfigProperties();

        try {
            Properties properties = new Properties();
            properties.load(new StringReader(content));

            // 解析全局配置
            String enable = properties.getProperty("onethread.enable");
            if (enable != null) {
                bootstrapConfigProperties.setEnable(Boolean.parseBoolean(enable));
            }

            // 解析线程池配置列表
            List<ThreadPoolExecutorProperties> executors = new ArrayList<>();
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith(PREFIX)) {
                    // 提取线程池索引和属性名
                    String remaining = key.substring(PREFIX.length());
                    int dotIndex = remaining.indexOf('.');
                    if (dotIndex > 0) {
                        String index = remaining.substring(0, dotIndex);
                        String propName = remaining.substring(dotIndex + 1);

                        // 获取或创建该索引对应的线程池配置
                        int idx = Integer.parseInt(index);
                        while (executors.size() <= idx) {
                            executors.add(new ThreadPoolExecutorProperties());
                        }
                        ThreadPoolExecutorProperties executorProps = executors.get(idx);

                        // 设置属性
                        String value = properties.getProperty(key);
                        setProperty(executorProps, propName, value);
                    }
                }
            }
            bootstrapConfigProperties.setExecutors(executors);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse properties config", e);
        }

        return bootstrapConfigProperties;
    }

    private void setProperty(ThreadPoolExecutorProperties executorProps, String propName, String value) {
        switch (propName) {
            case "threadPoolId":
                executorProps.setThreadPoolId(value);
                break;
            case "corePoolSize":
                executorProps.setCorePoolSize(Integer.parseInt(value));
                break;
            case "maximumPoolSize":
                executorProps.setMaximumPoolSize(Integer.parseInt(value));
                break;
            case "queueCapacity":
                executorProps.setQueueCapacity(Integer.parseInt(value));
                break;
            case "workQueue":
                executorProps.setWorkQueue(value);
                break;
            case "rejectedHandler":
                executorProps.setRejectedHandler(value);
                break;
            case "keepAliveTime":
                executorProps.setKeepAliveTime(Long.parseLong(value));
                break;
            case "allowCoreThreadTimeOut":
                executorProps.setAllowCoreThreadTimeOut(Boolean.parseBoolean(value));
                break;
            default:
                // ignore unknown properties
                break;
        }
    }
}
