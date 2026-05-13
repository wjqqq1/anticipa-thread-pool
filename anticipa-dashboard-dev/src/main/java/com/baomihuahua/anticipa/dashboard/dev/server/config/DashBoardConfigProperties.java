package com.baomihuahua.anticipa.dashboard.dev.server.config;

import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolDetailRespDTO;
import lombok.Data;

import java.util.List;

@Data
public class DashBoardConfigProperties {

    private List<ThreadPoolDetailRespDTO> executors;

    private WebThreadPoolExecutorConfig web;

    @Data
    public static class WebThreadPoolExecutorConfig {

        private Integer corePoolSize;

        private Integer maximumPoolSize;

        private Long keepAliveTime;

        private NotifyConfig notify;

        @Data
        public static class NotifyConfig {

            private String receives;
        }
    }
}
