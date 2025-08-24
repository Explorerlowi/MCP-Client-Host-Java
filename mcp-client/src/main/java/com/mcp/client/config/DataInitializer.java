package com.mcp.client.config;

import com.mcp.client.model.McpServerSpec;
import com.mcp.client.model.TransportType;
import com.mcp.client.repository.McpServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库初始化器
 * 在应用启动时初始化 Bazi 和 bilibili 服务器配置
 */
@Slf4j
@Component
@Order(1) // 确保在 MCPConfigurationLoader 之前执行
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final McpServerRepository repository;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始初始化 MCP 服务器配置数据...");
        
        try {
            initializeBaziServer();
            initializeBilibiliServer();
            
            log.info("MCP 服务器配置数据初始化完成");
        } catch (Exception e) {
            log.error("初始化 MCP 服务器配置数据失败", e);
        }
    }

    /**
     * 初始化 Bazi 服务器配置
     */
    private void initializeBaziServer() {
        String serverId = "Bazi";
        
        if (repository.existsById(serverId)) {
            log.debug("Bazi 服务器配置已存在，跳过初始化");
            return;
        }

        McpServerSpec baziServer = McpServerSpec.builder()
                .id(serverId)
                .name("Bazi")
                .description("八字命理分析服务器")
                .type(TransportType.STDIO)
                .command("npx")
                .args(Arrays.asList("bazi-mcp"))
                .timeout(60L)
                .disabled(false)
                .build();

        repository.save(baziServer);
        log.info("成功初始化 Bazi 服务器配置");
    }

    /**
     * 初始化 bilibili 服务器配置
     */
    private void initializeBilibiliServer() {
        String serverId = "bilibili";
        
        if (repository.existsById(serverId)) {
            log.debug("bilibili 服务器配置已存在，跳过初始化");
            return;
        }

        McpServerSpec bilibiliServer = McpServerSpec.builder()
                .id(serverId)
                .name("bilibili")
                .description("Bilibili API 服务器")
                .type(TransportType.STDIO)
                .command("uvx")
                .args(Arrays.asList("--index-url","https://mirrors.aliyun.com/pypi/simple/","bilibili-api-mcp-server"))
                .timeout(60L)
                .disabled(false)
                .build();

        repository.save(bilibiliServer);
        log.info("成功初始化 bilibili 服务器配置");
    }

    /**
     * 初始化基础服务器配置（不包含密钥的服务器）
     * 只初始化不需要API密钥的安全服务器
     */
    public void initializeBasicServers() {
        log.info("开始初始化基础 MCP 服务器配置（不包含密钥）...");

        // Bazi (无密钥)
        initializeBaziServer();

        // bilibili (无密钥)
        initializeBilibiliServer();

        log.info("基础 MCP 服务器配置初始化完成");
    }

    /**
     * 通用服务器初始化方法
     */
    private void initializeServer(String id, String name, String description, TransportType type,
                                String url, String command, List<String> args, Map<String, String> env) {
        if (repository.existsById(id)) {
            log.debug("{} 服务器配置已存在，跳过初始化", id);
            return;
        }

        McpServerSpec.McpServerSpecBuilder builder = McpServerSpec.builder()
                .id(id)
                .name(name)
                .description(description)
                .type(type)
                .timeout(60L)
                .disabled(false);

        if (url != null) {
            builder.url(url);
        }
        if (command != null) {
            builder.command(command);
        }
        if (args != null) {
            builder.args(args);
        }
        if (env != null) {
            builder.env(env);
        }

        repository.save(builder.build());
        log.info("成功初始化 {} 服务器配置", id);
    }
}
