package com.baomihuahua.anticipa.dashboard.dev.server.config;

import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolDetailRespDTO;
import lombok.Data;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

@Data
public class DashBoardConfigProperties {

    private List<ThreadPoolDetailRespDTO> executors;

    @NestedConfigurationProperty
    private WebThreadPoolExecutorConfig web;

    @Data
    public static class WebThreadPoolExecutorConfig {

        private Integer corePoolSize;

        private Integer maximumPoolSize;

        private Integer keepAliveTime;

        @NestedConfigurationProperty
        private NotifyConfig notify;

        @Data
        public static class NotifyConfig {

            private List<String> notifyPlatforms;

            private Integer interval;
        }
    }
}
