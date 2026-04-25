package com.baomihuahua.anticipa.core.parser;

import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import org.yaml.snakeyaml.Yaml;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 配置解析处理器
 * <p>
 * 根据配置类型选择合适的解析器进行解析
 * </p>
 */
public class ConfigParserHandler {

    private static final ConfigParserHandler INSTANCE = new ConfigParserHandler();

    public static ConfigParserHandler getInstance() {
        return INSTANCE;
    }

    /**
     * 解析配置内容为 Map
     *
     * @param configInfo     配置内容
     * @param configFileType 配置文件类型（yaml/properties 字符串）
     * @return 解析后的配置 Map
     */
    public Map<Object, Object> parseConfig(String configInfo, String configFileType) {
        return parseConfig(configInfo, ConfigFileTypeEnum.of(configFileType));
    }

    /**
     * 解析配置内容为 Map
     *
     * @param configInfo     配置内容
     * @param configFileType 配置文件类型
     * @return 解析后的配置 Map
     */
    public Map<Object, Object> parseConfig(String configInfo, ConfigFileTypeEnum configFileType) {
        switch (configFileType) {
            case YAML:
                Yaml yaml = new Yaml();
                return yaml.loadAs(configInfo, Map.class);
            case PROPERTIES:
                Properties props = new Properties();
                try {
                    props.load(new StringReader(configInfo));
                    return new HashMap<>(props);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse properties config", e);
                }
            default:
                throw new IllegalArgumentException("Unsupported config file type: " + configFileType);
        }
    }

    /**
     * 解析配置内容
     *
     * @param configFileType 配置文件类型
     * @param content        配置文件内容
     * @return 解析后的配置属性对象
     */
    public static BootstrapConfigProperties parseConfig(ConfigFileTypeEnum configFileType, String content) {
        AbstractConfigParser parser = getParser(configFileType);
        return parser.parse(content);
    }

    private static AbstractConfigParser getParser(ConfigFileTypeEnum configFileType) {
        switch (configFileType) {
            case YAML:
                return new YamlConfigParser();
            case PROPERTIES:
                return new PropertiesConfigParser();
            default:
                throw new IllegalArgumentException("Unsupported config file type: " + configFileType);
        }
    }
}
