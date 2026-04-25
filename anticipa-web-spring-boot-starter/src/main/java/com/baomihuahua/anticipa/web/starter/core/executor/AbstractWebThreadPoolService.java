package com.baomihuahua.anticipa.web.starter.core.executor;

import com.baomihuahua.anticipa.spring.base.support.ApplicationContextHolder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.Executor;

/**
 * 抽象 Web 容器线程池接口
 */
public abstract class AbstractWebThreadPoolService implements WebThreadPoolService, ApplicationRunner {

    /**
     * Web 容器底层线程池
     */
    protected Executor executor;

    /**
     * 获取当前 Web 容器的线程池执行器
     *
     * @param webServer Web 服务器，例如 Tomcat、Jetty 等
     * @return 当前 Web 容器的 Executor 实例
     */
    protected abstract Executor getExecutor(WebServer webServer);

    @Override
    public String getRunningStatus() {
        return "Running"; // 非核心数据，大家有兴趣可以自行扩展
    }

    public WebServer getWebServer() {
        ApplicationContext applicationContext = ApplicationContextHolder.getInstance();
        return ((WebServerApplicationContext) applicationContext).getWebServer();
    }

    @Override
    public void run(ApplicationArguments args) {
        Executor webExecutor = getExecutor(getWebServer());
        executor = webExecutor;
    }
}
