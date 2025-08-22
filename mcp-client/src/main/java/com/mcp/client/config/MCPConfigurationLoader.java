package com.mcp.client.config;

import com.mcp.client.service.MCPConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

            // 如果数据库中没有配置，尝试从文件迁移
            if (loadedCount == 0) {
                Path rootConfigPath = Paths.get("mcp-config.json");
                if (Files.exists(rootConfigPath)) {
                    log.info("数据库中无配置，尝试从文件迁移: {}", rootConfigPath);
                    configurationService.migrateFromFile(rootConfigPath.toString());
                    log.info("配置文件迁移完成");
                } else {
                    log.warn("数据库和配置文件都不存在，将无法加载任何 MCP 服务器");
                }
            }
        } catch (Exception e) {
            log.error("加载 MCP 服务器配置失败", e);
            throw e;
        }
    }
}