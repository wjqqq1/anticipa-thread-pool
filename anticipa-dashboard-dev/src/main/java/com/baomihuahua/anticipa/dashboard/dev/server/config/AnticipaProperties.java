package com.baomihuahua.anticipa.dashboard.dev.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "anticipa")
public class AnticipaProperties {

    /**
     * 用户集合，格式: username,password
     */
    private List<String> users;

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

    /**
     * 免登录（白名单）接口路径前缀列表
     */
    private List<String> excludePaths = new ArrayList<>();

    /**
     * Grafana 监控面板配置
     */
    private Grafana grafana = new Grafana();

    @Data
    public static class Grafana {
        /**
         * Grafana 仪表盘 URL 地址
         * 示例: http://grafana.example.com/d/xxx?orgId=1&from=now-6h&to=now&theme=light&kiosk=true
         */
        private String url;
    }
}
