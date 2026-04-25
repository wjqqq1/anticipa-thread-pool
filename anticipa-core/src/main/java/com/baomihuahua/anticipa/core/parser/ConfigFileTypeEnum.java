package com.baomihuahua.anticipa.core.parser;

/**
 * 配置文件类型枚举
 */
public enum ConfigFileTypeEnum {

    /**
     * YAML 格式配置文件
     */
    YAML("yaml"),

    /**
     * Properties 格式配置文件
     */
    PROPERTIES("properties");

    private final String type;

    ConfigFileTypeEnum(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    /**
     * 根据类型名称获取枚举
     *
     * @param type 类型名称
     * @return 配置类型枚举
     */
    public static ConfigFileTypeEnum of(String type) {
        for (ConfigFileTypeEnum value : values()) {
            if (value.type.equalsIgnoreCase(type)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported config file type: " + type);
    }
}
