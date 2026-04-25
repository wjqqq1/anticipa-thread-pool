package com.baomihuahua.anticipa.web.starter.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Web 容器类型枚举
 */
@RequiredArgsConstructor
public enum WebContainerEnum {

    /**
     * Tomcat
     */
    TOMCAT("Tomcat"),

    /**
     * Jetty
     */
    JETTY("Jetty"),

    /**
     * Undertow
     */
    UNDERTOW("Undertow");

    @Getter
    private final String name;

    @Override
    public String toString() {
        return getName();
    }
}
