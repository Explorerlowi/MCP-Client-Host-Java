package com.mcp.client.config;

import com.mcp.client.service.MCPConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * MCP 配置加载器
 * 在应用启动时自动加载 MCP 服务器配置
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MCPConfigurationLoader implements CommandLineRunner {

    private final MCPConfigurationService configurationService;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始从数据库加载 MCP 服务器配置...");

        try {
            // 从数据库加载配置
            int loadedCount = configurationService.loadConfigurationFromDatabase();
            log.info("从数据库加载 MCP 服务器配置完成，共加载 {} 个服务器", loadedCount);

            if (loadedCount == 0) {
                log.warn("数据库中没有找到任何 MCP 服务器配置");
            }
        } catch (Exception e) {
            log.error("加载 MCP 服务器配置失败", e);
            throw e;
        }
    }
}