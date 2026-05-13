package com.baomihuahua.anticipa.dashboard.dev.server;

import com.baomihuahua.anticipa.dashboard.dev.server.config.AnticipaProperties;
import com.baomihuahua.anticipa.dashboard.dev.server.config.DashboardScheduledTaskAutoConfiguration;
import com.baomihuahua.anticipa.dashboard.dev.server.controller.ScheduledTaskController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(DashboardScheduledTaskAutoConfiguration.class)
@EnableConfigurationProperties(AnticipaProperties.class)
@ComponentScan(excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = ScheduledTaskController.class))
public class AnticipaDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnticipaDashboardApplication.class, args);
    }
}
