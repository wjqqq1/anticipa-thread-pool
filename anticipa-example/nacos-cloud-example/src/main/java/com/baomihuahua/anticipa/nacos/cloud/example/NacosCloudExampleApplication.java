package com.baomihuahua.anticipa.nacos.cloud.example;

import com.baomihuahua.anticipa.spring.base.enable.EnableAnticipa;
import com.baomihuahua.anticipa.spring.base.enable.EnableThreadPoolLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableAnticipa
@EnableThreadPoolLog
@SpringBootApplication
public class NacosCloudExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(NacosCloudExampleApplication.class, args);
    }
}
