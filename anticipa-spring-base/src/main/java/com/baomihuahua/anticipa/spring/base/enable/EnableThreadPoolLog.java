package com.baomihuahua.anticipa.spring.base.enable;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用线程池日志统计功能注解。
 * <p>
 * 在 Spring Boot 启动类上添加此注解，即可开启线程池运行指标的定期采集与持久化。
 * 也可通过 YAML 配置 {@code anticipa.log.enabled=true} 达到同样效果，二者任一开启即生效。
 * </p>
 *
 * <pre>
 * &#64;EnableAnticipa
 * &#64;EnableThreadPoolLog
 * &#64;SpringBootApplication
 * public class MyApplication { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ThreadPoolLogMarkerConfiguration.class)
public @interface EnableThreadPoolLog {
}
