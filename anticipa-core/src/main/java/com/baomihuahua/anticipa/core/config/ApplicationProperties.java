package com.baomihuahua.anticipa.core.config;

import lombok.Getter;
import lombok.Setter;

/**
 * 应用属性配置
 */
public class ApplicationProperties {

    /**
     * 应用名
     */
    @Getter
    @Setter
    private static String applicationName;

    /**
     * 环境标识
     */
    @Getter
    @Setter
    private static String activeProfile;
}
