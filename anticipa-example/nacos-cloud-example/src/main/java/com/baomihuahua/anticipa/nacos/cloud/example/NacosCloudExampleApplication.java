package com.baomihuahua.anticipa.nacos.cloud.example;

import com.baomihuahua.anticipa.spring.base.enable.EnableAnticipa;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableAnticipa
@SpringBootApplication
public class NacosCloudExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(NacosCloudExampleApplication.class, args);
    }
}
