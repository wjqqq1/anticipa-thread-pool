package com.baomihuahua.anticipa.core.parser;

/**
 * 配置解析器接口
 */
public interface ConfigParser {

    /**
     * 将配置文件内容解析为字符串
     *
     * @param content 配置文件内容
     * @return 解析后的字符串
     */
    String parse(String content);
}
