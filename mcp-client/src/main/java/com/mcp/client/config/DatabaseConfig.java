package com.mcp.client.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 数据库配置类
 */
@Slf4j
@Configuration
@EnableJpaRepositories(basePackages = "com.mcp.client.repository")
@EnableTransactionManagement
public class DatabaseConfig {
    
    /**
     * 应用启动完成后的初始化操作
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("MCP Client 数据库初始化完成");
        log.info("H2 控制台访问地址: http://localhost:8086/h2-console");
        log.info("数据库连接 URL: jdbc:h2:mem:mcpclient");
        log.info("用户名: sa, 密码: (空)");
    }
}