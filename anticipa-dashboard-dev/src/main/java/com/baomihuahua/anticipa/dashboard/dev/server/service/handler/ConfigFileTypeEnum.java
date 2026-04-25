package com.baomihuahua.anticipa.dashboard.dev.server.service.handler;

import lombok.Getter;

@Getter
public enum ConfigFileTypeEnum {

    PROPERTIES("properties"),
    YML("yml"),
    YAML("yaml");

    private final String value;

    ConfigFileTypeEnum(String value) {
        this.value = value;
    }

    public static ConfigFileTypeEnum of(String value) {
        for (ConfigFileTypeEnum typeEnum : ConfigFileTypeEnum.values()) {
            if (typeEnum.value.equals(value)) {
                return typeEnum;
            }
        }
        return PROPERTIES;
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
