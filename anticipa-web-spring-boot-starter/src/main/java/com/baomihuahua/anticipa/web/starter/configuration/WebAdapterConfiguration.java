package com.baomihuahua.anticipa.web.starter.configuration;

import com.baomihuahua.anticipa.core.notification.service.NotifierDispatcher;
import com.baomihuahua.anticipa.web.starter.core.WebThreadPoolRefreshListener;
import com.baomihuahua.anticipa.web.starter.core.executor.JettyWebThreadPoolService;
import com.baomihuahua.anticipa.web.starter.core.executor.TomcatWebThreadPoolService;
import com.baomihuahua.anticipa.web.starter.core.executor.WebThreadPoolService;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.web.embedded.jetty.ConfigurableJettyWebServerFactory;
import org.springframework.boot.web.embedded.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.context.annotation.Bean;

/**
 * Web 容器动态线程池适配自动装配配置类
 */
@Configurable
public class WebAdapterConfiguration {

    @Bean
    @ConditionalOnClass(name = {"org.apache.catalina.startup.Tomcat", "org.apache.coyote.UpgradeProtocol", "jakarta.servlet.Servlet"})
    @ConditionalOnBean(value = ConfigurableTomcatWebServerFactory.class, search = SearchStrategy.CURRENT)
    public TomcatWebThreadPoolService tomcatWebThreadPoolService() {
        return new TomcatWebThreadPoolService();
    }

    @Bean
    @ConditionalOnClass(
            name = {
                    "jakarta.servlet.Servlet", "org.eclipse.jetty.server.Server",
                    "org.eclipse.jetty.util.Loader", "org.eclipse.jetty.ee10.webapp.WebAppContext"
            })
    @ConditionalOnBean(value = ConfigurableJettyWebServerFactory.class, search = SearchStrategy.CURRENT)
    public JettyWebThreadPoolService jettyWebThreadPoolService() {
        return new JettyWebThreadPoolService();
    }

    @Bean
    public WebThreadPoolRefreshListener webThreadPoolRefreshListener(@SuppressWarnings("all")
                                                                     WebThreadPoolService webThreadPoolService,
                                                                     NotifierDispatcher notifierDispatcher) {
        return new WebThreadPoolRefreshListener(webThreadPoolService, notifierDispatcher);
    }
}
