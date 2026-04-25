package com.baomihuahua.anticipa.core.parser;

import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;

/**
 * 配置解析器抽象类
 * <p>
 * 定义配置文件解析的抽象方法，子类实现不同格式的解析
 * </p>
 */
public abstract class AbstractConfigParser {

    /**
     * 解析配置文件内容为配置属性对象
     *
     * @param content 配置文件内容
     * @return 配置属性对象
     */
    public abstract BootstrapConfigProperties parse(String content);
}
