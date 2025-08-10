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
        log.info("开始加载 MCP 服务器配置...");

        // 只从根目录的 mcp-config.json 文件加载配置
        Path rootConfigPath = Paths.get("mcp-config.json");

        if (Files.exists(rootConfigPath)) {
            log.info("从根目录配置文件加载: {}", rootConfigPath);
            configurationService.loadConfigurationFromFile(rootConfigPath.toString());
            log.info("MCP 服务器配置加载完成");
        } else {
            log.warn("根目录配置文件不存在: {}", rootConfigPath);
            log.warn("请确保配置文件存在，否则将无法加载任何 MCP 服务器");
        }
    }
}