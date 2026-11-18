package com.pandago;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PandaGo 商城后端启动类。
 *
 * <p>{@link SpringBootApplication} 组合了配置类扫描、自动配置与组件扫描，
 * 默认扫描本包及其子包下的所有 Spring Bean。因此业务模块必须放在
 * {@code com.pandago} 包下才能被容器管理。</p>
 */
@SpringBootApplication
@EnableScheduling
public class PandaGoApplication {

    public static void main(String[] args) {
        SpringApplication.run(PandaGoApplication.class, args);
    }
}
