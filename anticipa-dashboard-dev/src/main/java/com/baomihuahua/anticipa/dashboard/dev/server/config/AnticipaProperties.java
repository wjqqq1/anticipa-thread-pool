package com.baomihuahua.anticipa.dashboard.dev.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "anticipa")
public class AnticipaProperties {

    /**
     * Nacos 服务地址
     */
    private String nacosAddr;

    /**
     * Nacos 命名空间列表
     */
    private List<String> namespaces;

    /**
     * Nacos 用户名
     */
    private String nacosUsername;

    /**
     * Nacos 密码
     */
    private String nacosPassword;
}
