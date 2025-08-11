package com.mcp.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.client.config.MCPServerConfig;
import com.mcp.client.model.McpServerSpec;
import com.mcp.client.model.TransportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    
    /**
     * 从 JSON 文件加载配置
     * @param configFile 配置文件路径
     */
    public void loadConfigurationFromFile(String configFile) {
        try {
            File file = new File(configFile);
            if (!file.exists()) {
                log.warn("配置文件不存在: {}", configFile);
                return;
            }
            
            MCPServerConfig config = objectMapper.readValue(file, MCPServerConfig.class);
            loadConfiguration(config);
            
        } catch (IOException e) {
            log.error("加载配置文件失败: {}", configFile, e);
            throw new RuntimeException("配置文件加载失败", e);
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
                .transport(transportType)
                .disabled(config.isDisabled());
        
        // 设置超时时间（秒）
        if (config.getTimeout() != null) {
            builder.timeout(config.getTimeout());
        }
        
        // 根据传输类型设置相应的配置
        switch (transportType) {
            case STDIO:
                if (config.getCommand() == null) {
                    throw new IllegalArgumentException("STDIO 传输需要指定 command");
                }
                builder.command(config.getCommand());
                if (config.getArgs() != null) {
                    builder.args(Arrays.asList(config.getArgs()));
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
     * 导出当前配置为 JSON
     * @return JSON 配置字符串
     */
    public String exportConfigurationAsJson() {
        try {
            List<McpServerSpec> specs = serverRegistry.getAllSpecs();
            MCPServerConfig config = new MCPServerConfig();

            Map<String, MCPServerConfig.ServerConfig> mcpServers = specs.stream()
                    .collect(Collectors.toMap(
                            McpServerSpec::getId,
                            this::convertFromServerSpec
                    ));

            config.setMcpServers(mcpServers);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);

        } catch (Exception e) {
            log.error("导出配置失败", e);
            throw new RuntimeException("配置导出失败", e);
        }
    }

    /**
     * 保存当前配置到文件
     * @param configFile 配置文件路径
     */
    public void saveConfigurationToFile(String configFile) {
        try {
            String configJson = exportConfigurationAsJson();
            File file = new File(configFile);

            // 确保父目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 写入文件
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                writer.write(configJson);
            }

            log.info("成功保存配置到文件: {}", configFile);

        } catch (IOException e) {
            log.error("保存配置文件失败: {}", configFile, e);
            throw new RuntimeException("配置文件保存失败", e);
        }
    }

    /**
     * 保存当前配置到默认配置文件 (mcp-config.json)
     */
    public void saveConfigurationToDefaultFile() {
        saveConfigurationToFile("mcp-config.json");
    }
    
    /**
     * 将服务器规格转换为配置
     * @param spec 服务器规格
     * @return 服务器配置
     */
    private MCPServerConfig.ServerConfig convertFromServerSpec(McpServerSpec spec) {
        MCPServerConfig.ServerConfig config = new MCPServerConfig.ServerConfig();
        config.setDisabled(spec.isDisabled());
        config.setType(spec.getTransport().name().toLowerCase());
        config.setTimeout(spec.getTimeout());
        
        switch (spec.getTransport()) {
            case STDIO:
                config.setCommand(spec.getCommand());
                if (spec.getArgs() != null) {
                    config.setArgs(spec.getArgs().toArray(new String[0]));
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