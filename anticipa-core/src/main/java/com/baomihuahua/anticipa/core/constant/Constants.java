package com.baomihuahua.anticipa.core.constant;

/**
 * 动态线程池基础常量类
 */
public class Constants {

    /**
     * 线程池参数变更日志打印常量
     */
    public static final String CHANGE_THREAD_POOL_TEXT = "[{}] Dynamic thread pool parameter changed:"
            + "\n    corePoolSize: {}"
            + "\n    maximumPoolSize: {}"
            + "\n    capacity: {}"
            + "\n    keepAliveTime: {}"
            + "\n    rejectedType: {}"
            + "\n    allowCoreThreadTimeOut: {}";

    /**
     * 线程池参数变更前后分隔符常量
     */
    public static final String CHANGE_DELIMITER = "%s => %s";

    /**
     * 钉钉配置变更消息文本
     */
    public static final String DING_CONFIG_CHANGE_MESSAGE_TEXT = """
            **<font color=#2a9d8f>[通知] </font>%s - 动态线程池参数变更**
            
             ---
            
            <font color='#708090' size=2>线程池ID：%s</font>\s
            
            <font color='#708090' size=2>应用实例：%s</font>\s
            
            <font color='#708090' size=2>核心线程数：%s</font>\s
            
            <font color='#708090' size=2>最大线程数：%s</font>\s
            
            <font color='#708090' size=2>线程存活时间：%s</font>\s
            
            <font color='#708090' size=2>队列类型：%s</font>\s
            
            <font color='#708090' size=2>队列容量：%s</font>\s
            
            <font color='#708090' size=2>旧拒绝策略：%s</font>\s
            
            <font color='#708090' size=2>新拒绝策略：%s</font>\s
            
            <font color='#708090' size=2>OWNER：@%s</font>\s
            
            <font color='#708090' size=2>提示：动态线程池配置变更实时通知（无限制）</font>\s
            
             ---
            
            **变更时间：%s**
            """;

    /**
     * 钉钉配置 Web 线程池变更消息文本
     */
    public static final String DING_CONFIG_WEB_CHANGE_MESSAGE_TEXT = """
            **<font color=#2a9d8f>[通知] </font>%s - %s线程池参数变更**
            
             ---
            
            <font color='#708090' size=2>应用实例：%s</font>\s
            
            <font color='#708090' size=2>核心线程数：%s</font>\s
            
            <font color='#708090' size=2>最大线程数：%s</font>\s
            
            <font color='#708090' size=2>线程存活时间：%s</font>\s
            
            <font color='#708090' size=2>OWNER：@%s</font>\s
            
            <font color='#708090' size=2>提示：%s线程池配置变更实时通知（无限制）</font>\s
            
             ---
            
            **变更时间：%s**
            """;

    /**
     * 钉钉配置报警消息文本
     */
    public static final String DING_ALARM_NOTIFY_MESSAGE_TEXT = """
            **<font color=#FF0000>[警报] </font>%s - 动态线程池运行告警**
            
             ---
            
            <font color='#708090' size=2>线程池ID：%s</font>\s
            
            <font color='#708090' size=2>应用实例：%s</font>\s
            
            <font color='#708090' size=2>告警类型：%s</font>\s
            
             ---
            
            <font color='#708090' size=2>核心线程数：%d</font>\s
            
            <font color='#708090' size=2>最大线程数：%d</font>\s
            
            <font color='#708090' size=2>当前线程数：%d</font>\s
            
            <font color='#708090' size=2>活跃线程数：%d</font>\s
            
            <font color='#708090' size=2>同存最大线程数：%d</font>\s
            
            <font color='#708090' size=2>线程池任务总量：%d</font>\s
            
             ---
            
            <font color='#708090' size=2>队列类型：%s</font>\s
            
            <font color='#708090' size=2>队列容量：%d</font>\s
            
            <font color='#708090' size=2>队列元素个数：%d</font>\s
            
            <font color='#708090' size=2>队列剩余个数：%d</font>\s
            
             ---
            
            <font color='#708090' size=2>拒绝策略：%s</font>\s
            
            <font color='#708090' size=2>拒绝策略执行次数：</font><font color='#FF0000' size=2>%d</font>\s
            
            <font color='#708090' size=2>OWNER：@%s</font>\s
            
            <font color='#708090' size=2>提示：%d分钟内此线程池不会重复告警（可配置）</font>\s
            
             ---
            
            **告警时间：%s**
            """;
}
