package com.mcp.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.client.config.MCPServerConfig;
import com.mcp.client.model.McpServerSpec;
import com.mcp.client.model.TransportType;
import com.mcp.client.repository.McpServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP 配置服务
 * 负责加载和管理 MCP 服务器配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MCPConfigurationService {

    private final ObjectMapper objectMapper;
    private final MCPServerRegistry serverRegistry;
    private final McpServerRepository repository;
    
    /**
     * 从数据库加载配置
     * @return 加载的服务器数量
     */
    public int loadConfigurationFromDatabase() {
        try {
            List<McpServerSpec> specs = repository.findAll();
            log.info("从数据库找到 {} 个 MCP 服务器配置", specs.size());

            int loadedCount = 0;
            for (McpServerSpec spec : specs) {
                try {
                    serverRegistry.register(spec);
                    log.info("成功注册 MCP 服务器: {}", spec.getId());
                    loadedCount++;
                } catch (Exception e) {
                    log.error("注册 MCP 服务器失败: {}", spec.getId(), e);
                }
            }

            return loadedCount;
        } catch (Exception e) {
            log.error("从数据库加载配置失败", e);
            throw new RuntimeException("数据库配置加载失败", e);
        }
    }

    /**
     * 从 JSON 字符串加载配置
     * @param jsonConfig JSON 配置字符串
     * @return 加载的服务器数量
     */
    public int loadConfigurationFromJson(String jsonConfig) {
        try {
            MCPServerConfig config = objectMapper.readValue(jsonConfig, MCPServerConfig.class);
            return loadConfiguration(config);

        } catch (IOException e) {
            log.error("解析 JSON 配置失败", e);
            throw new RuntimeException("JSON 配置解析失败", e);
        }
    }

    /**
     * 加载配置对象
     * @param config 配置对象
     * @return 成功加载的服务器数量
     */
    public int loadConfiguration(MCPServerConfig config) {
        if (config.getMcpServers() == null) {
            log.warn("没有找到 MCP 服务器配置");
            return 0;
        }

        int loadedCount = 0;
        for (Map.Entry<String, MCPServerConfig.ServerConfig> entry : config.getMcpServers().entrySet()) {
            String serverId = entry.getKey();
            MCPServerConfig.ServerConfig serverConfig = entry.getValue();

            try {
                McpServerSpec spec = convertToServerSpec(serverId, serverConfig);
                serverRegistry.register(spec);
                log.info("成功注册 MCP 服务器: {}", serverId);
                loadedCount++;

            } catch (Exception e) {
                log.error("注册 MCP 服务器失败: {}", serverId, e);
            }
        }

        return loadedCount;
    }

    /**
     * 将配置转换为服务器规格
     * @param serverId 服务器ID
     * @param config 服务器配置
     * @return 服务器规格
     */
    private McpServerSpec convertToServerSpec(String serverId, MCPServerConfig.ServerConfig config) {
        // 确定传输类型
        TransportType transportType = determineTransportType(config);

        // 构建服务器规格
        McpServerSpec.McpServerSpecBuilder builder = McpServerSpec.builder()
                .id(serverId)
                .name(serverId) // 使用 ID 作为默认名称
                .type(transportType)
                .disabled(config.isDisabled());

        // 设置描述
        if (config.getDescription() != null) {
            builder.description(config.getDescription());
        }

        // 设置超时时间（秒）
        if (config.getTimeout() != null) {
            builder.timeout(config.getTimeout());
        } else {
            builder.timeout(60L); // 默认 60 秒
        }

        // 根据传输类型设置相应的配置
        switch (transportType) {
            case STDIO:
                if (config.getCommand() == null) {
                    throw new IllegalArgumentException("STDIO 传输需要指定 command");
                }
                builder.command(config.getCommand());
                if (config.getArgs() != null && config.getArgs().length > 0) {
                    builder.args(String.join(" ", config.getArgs()));
                }
                if (config.getEnv() != null) {
                    builder.env(config.getEnv());
                }
                break;

            case SSE:
            case STREAMABLEHTTP:
                if (config.getUrl() == null) {
                    throw new IllegalArgumentException(transportType + " 传输需要指定 URL");
                }
                builder.url(config.getUrl());
                break;

            default:
                throw new IllegalArgumentException("不支持的传输类型: " + transportType);
        }

        return builder.build();
    }

    /**
     * 确定传输类型
     * @param config 服务器配置
     * @return 传输类型
     */
    private TransportType determineTransportType(MCPServerConfig.ServerConfig config) {
        // 使用 type 字段确定传输类型
        String transportValue = config.getType();

        if (transportValue != null) {
            return switch (transportValue.toLowerCase()) {
                case "stdio" -> TransportType.STDIO;
                case "sse" -> TransportType.SSE;
                case "http", "streamablehttp" -> TransportType.STREAMABLEHTTP;
                default -> throw new IllegalArgumentException("不支持的传输类型: " + transportValue);
            };
        }

        // 根据配置自动推断传输类型
        if (config.getCommand() != null) {
            return TransportType.STDIO;
        } else if (config.getUrl() != null) {
            if (config.getUrl().contains("/sse")) {
                return TransportType.SSE;
            } else {
                return TransportType.STREAMABLEHTTP;
            }
        }

        // 默认使用 STDIO
        return TransportType.STDIO;
    }

    /**
     * 导出所有服务器配置为 JSON 字符串
     * @return JSON 配置字符串
     */
    public String exportConfigurationToJson() {
        try {
            // 从数据库获取所有服务器配置
            List<McpServerSpec> specs = repository.findAll();
            log.info("准备导出 {} 个服务器配置", specs.size());

            // 转换为 MCPServerConfig 格式
            Map<String, MCPServerConfig.ServerConfig> mcpServers = specs.stream()
                    .collect(Collectors.toMap(
                            McpServerSpec::getId,
                            this::convertToServerConfig
                    ));

            MCPServerConfig config = new MCPServerConfig();
            config.setMcpServers(mcpServers);

            // 序列化为 JSON
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
            log.info("配置导出成功");
            return json;

        } catch (Exception e) {
            log.error("导出配置失败", e);
            throw new RuntimeException("配置导出失败", e);
        }
    }

    /**
     * 将 McpServerSpec 转换为 ServerConfig
     * @param spec 服务器规格
     * @return 服务器配置
     */
    private MCPServerConfig.ServerConfig convertToServerConfig(McpServerSpec spec) {
        MCPServerConfig.ServerConfig config = new MCPServerConfig.ServerConfig();

        // 设置基本信息
        config.setType(spec.getType().name().toLowerCase());
        config.setDisabled(spec.isDisabled());
        config.setDescription(spec.getDescription());
        config.setTimeout(spec.getTimeout());

        // 根据传输类型设置相应的配置
        switch (spec.getType()) {
            case STDIO:
                config.setCommand(spec.getCommand());
                if (spec.getArgs() != null && !spec.getArgs().isEmpty()) {
                    config.setArgs(spec.getArgs().split("\\s+"));
                }
                config.setEnv(spec.getEnv());
                break;

            case SSE:
            case STREAMABLEHTTP:
                config.setUrl(spec.getUrl());
                break;
        }

        return config;
    }

}