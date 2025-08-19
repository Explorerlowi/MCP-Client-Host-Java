package com.mcp.client.service.impl;

import com.mcp.client.exception.McpException;
import com.mcp.client.exception.McpServerNotFoundException;
import com.mcp.client.exception.UnsupportedTransportException;
import com.mcp.client.model.McpServerSpec;
import com.mcp.client.model.MCPTool;
import com.mcp.client.model.MCPResource;
import com.mcp.client.model.MCPPrompt;
import com.mcp.client.model.TransportType;
import com.mcp.client.repository.McpServerRepository;
import com.mcp.client.service.MCPClient;
import com.mcp.client.service.MCPConfigurationService;
import com.mcp.client.service.MCPServerRegistry;
import com.mcp.client.service.ConnectionRetryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP 服务器注册表实现类
 * 负责管理 MCP 服务器的注册、配置和生命周期
 */
@Slf4j
@Service
public class MCPServerRegistryImpl implements MCPServerRegistry {
    
    private final Map<String, McpServerSpec> specs = new ConcurrentHashMap<>();
    private final Map<String, MCPClient> clients = new ConcurrentHashMap<>();
    
    @Autowired
    private McpServerRepository repository;

    @Autowired
    @Lazy
    private MCPConfigurationService configurationService;

    @Autowired
    private ConnectionRetryManager retryManager;

    /**
     * 加载单个服务器配置到内存
     */
    private void loadServerSpec(McpServerSpec spec) {
        // 验证配置
        validateSpec(spec);

        // 注册到内存
        specs.put(spec.getId(), spec);

        // 只有未禁用的服务器才创建客户端连接
        if (!spec.isDisabled()) {
            try {
                MCPClient client = buildClient(spec);
                clients.put(spec.getId(), client);
                // 记录连接成功
                retryManager.recordSuccess(spec.getId());
            } catch (Exception e) {
                log.warn("创建 MCP 客户端连接失败: {}, 将在后续调用时重试", spec.getId());
                // 记录连接失败
                retryManager.recordFailure(spec.getId());
                // 不抛出异常，允许延迟连接
            }
        } else {
            log.debug("服务器已禁用，跳过创建客户端连接: {}", spec.getId());
        }
    }
    
    @Override
    @Transactional
    public void register(@Valid McpServerSpec spec) {
        log.info("注册 MCP 服务器: {}", spec.getId());
        
        try {
            // 验证配置
            validateSpec(spec);
            
            // 持久化配置
            repository.save(spec);
            log.debug("MCP 服务器配置已保存到数据库: {}", spec.getId());
            
            // 注册到内存
            specs.put(spec.getId(), spec);

            // 保存配置到文件（在创建客户端连接之前，确保配置已持久化）
            try {
                configurationService.saveConfigurationToDefaultFile();
                log.debug("已将配置保存到 mcp-config.json 文件");
            } catch (Exception e) {
                log.warn("保存配置到文件失败，但服务器注册成功: {}", e.getMessage());
                // 不抛出异常，因为服务器注册已经成功
            }

            // 只有未禁用的服务器才创建客户端连接
            if (!spec.isDisabled()) {
                clients.compute(spec.getId(), (k, oldClient) -> {
                    // 关闭旧连接
                    if (oldClient != null) {
                        try {
                            oldClient.close();
                            log.debug("已关闭旧的 MCP 客户端连接: {}", k);
                        } catch (Exception e) {
                            log.warn("关闭旧 MCP 客户端连接时发生错误: {}", k, e);
                        }
                    }

                    // 创建新连接
                    try {
                        MCPClient newClient = buildClient(spec);
                        // 记录连接成功
                        retryManager.recordSuccess(k);
                        log.info("成功创建 MCP 客户端连接: {}", k);
                        return newClient;
                    } catch (Exception e) {
                        // 记录连接失败
                        retryManager.recordFailure(k);
                        log.error("创建 MCP 客户端连接失败: {}", k, e);

                        // 检查是否应该禁用服务器
                        ConnectionRetryManager.FailureInfo failureInfo = retryManager.getFailureInfo(k);
                        if (failureInfo != null && failureInfo.shouldGiveUp()) {
                            log.warn("服务器 {} 注册时连续失败 {} 次，自动禁用服务器", k, failureInfo.getFailureCount());
                            // 更新spec为禁用状态，但不调用disableServerAfterFailure避免递归
                            spec.setDisabled(true);
                            repository.save(spec);
                            specs.put(k, spec);
                            retryManager.clearFailureInfo(k);
                            log.info("服务器 {} 已自动禁用", k);
                            return null; // 返回null表示连接失败但已处理
                        }

                        throw new McpException("创建客户端连接失败: " + e.getMessage(), e);
                    }
                });
            } else {
                // 如果服务器被禁用，关闭现有连接
                MCPClient oldClient = clients.remove(spec.getId());
                if (oldClient != null) {
                    try {
                        oldClient.close();
                        log.debug("服务器已禁用，已关闭 MCP 客户端连接: {}", spec.getId());
                    } catch (Exception e) {
                        log.warn("关闭 MCP 客户端连接时发生错误: {}", spec.getId(), e);
                    }
                }
                log.debug("服务器已禁用，跳过创建客户端连接: {}", spec.getId());
            }

            log.info("MCP 服务器注册成功: {}", spec.getId());
        } catch (Exception e) {
            log.error("注册 MCP 服务器失败: {}", spec.getId(), e);
            throw new McpException("注册服务器失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    @Transactional
    public void unregisterAll() {
        log.info("注销所有 MCP 服务器");
        
        try {
            // 从数据库删除所有
            repository.deleteAll();
            log.debug("已从数据库删除所有 MCP 服务器配置");
            
            // 关闭所有客户端连接
            for (Map.Entry<String, MCPClient> entry : clients.entrySet()) {
                try {
                    entry.getValue().close();
                    log.debug("已关闭 MCP 客户端连接: {}", entry.getKey());
                } catch (Exception e) {
                    log.warn("关闭 MCP 客户端连接时发生错误: {}", entry.getKey(), e);
                }
            }
            
            // 清空内存
            specs.clear();
            clients.clear();
            
            log.info("所有 MCP 服务器注销成功");
        } catch (Exception e) {
            log.error("注销所有 MCP 服务器失败", e);
            throw new McpException("注销所有服务器失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public McpServerSpec getSpec(String serverId) {
        McpServerSpec spec = specs.get(serverId);
        if (spec == null) {
            throw new McpServerNotFoundException("服务器配置未找到: " + serverId);
        }
        return spec;
    }
    
    @Override
    @Transactional
    public void unregister(String serverId) {
        log.info("注销 MCP 服务器: {}", serverId);
        
        try {
            // 从数据库删除
            if (repository.existsById(serverId)) {
                repository.deleteById(serverId);
                log.debug("已从数据库删除 MCP 服务器配置: {}", serverId);
            }
            
            // 从内存移除
            specs.remove(serverId);
            
            // 关闭客户端连接
            MCPClient client = clients.remove(serverId);
            if (client != null) {
                try {
                    client.close();
                    log.debug("已关闭 MCP 客户端连接: {}", serverId);
                } catch (Exception e) {
                    log.warn("关闭 MCP 客户端连接时发生错误: {}", serverId, e);
                }
            }

            // 保存配置到文件
            try {
                configurationService.saveConfigurationToDefaultFile();
                log.debug("已将配置保存到 mcp-config.json 文件");
            } catch (Exception e) {
                log.warn("保存配置到文件失败，但服务器注销成功: {}", e.getMessage());
                // 不抛出异常，因为服务器注销已经成功
            }

            log.info("MCP 服务器注销成功: {}", serverId);
        } catch (Exception e) {
            log.error("注销 MCP 服务器失败: {}", serverId, e);
            throw new McpException("注销服务器失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public MCPClient getClient(String serverId) {
        MCPClient client = clients.get(serverId);

        // 如果客户端不存在或连接已断开，尝试重新创建
        if (client == null || !client.isConnected()) {
            // 检查是否可以重试
            if (!retryManager.canRetry(serverId)) {
                if (client != null) {
                    return client; // 返回断开的客户端，让调用方处理
                }
                throw new McpException("服务器 " + serverId + " 暂时不可用，正在退避重试中");
            }

            McpServerSpec spec = specs.get(serverId);
            if (spec == null) {
                throw new McpServerNotFoundException("服务器未找到: " + serverId);
            }

            // 检查服务器是否被禁用
            if (spec.isDisabled()) {
                throw new McpException("服务器已被禁用: " + serverId);
            }

            try {
                log.debug("尝试创建/重新创建 MCP 客户端连接: {}", serverId);
                client = buildClient(spec);
                clients.put(serverId, client);

                // 记录连接成功
                retryManager.recordSuccess(serverId);
                log.info("MCP 客户端连接成功: {}", serverId);

            } catch (Exception e) {
                // 记录连接失败
                retryManager.recordFailure(serverId);
                log.error("创建 MCP 客户端连接失败: {}", serverId, e);

                // 检查是否应该禁用服务器
                ConnectionRetryManager.FailureInfo failureInfo = retryManager.getFailureInfo(serverId);
                if (failureInfo != null && failureInfo.shouldGiveUp()) {
                    log.warn("服务器 {} 连续失败 {} 次，自动禁用服务器", serverId, failureInfo.getFailureCount());
                    disableServerAfterFailure(serverId);
                }

                throw new McpException("获取客户端失败: " + e.getMessage(), e);
            }
        }

        return client;
    }

    @Override
    public MCPClient getExistingClient(String serverId) {
        return clients.get(serverId);
    }

    @Override
    public List<MCPTool> getAllTools() {
        return clients.entrySet().stream()
                .flatMap(entry -> {
                    try {
                        String serverId = entry.getKey();
                        MCPClient client = entry.getValue();

                        // 检查服务器是否被禁用
                        McpServerSpec spec = specs.get(serverId);
                        if (spec != null && spec.isDisabled()) {
                            log.debug("服务器已禁用，跳过获取工具: {}", serverId);
                            return java.util.stream.Stream.empty();
                        }

                        // 检查连接状态
                        if (!client.isConnected()) {
                            log.warn("MCP 服务器连接已断开，跳过获取工具: {}", serverId);
                            return java.util.stream.Stream.empty();
                        }

                        // 获取工具列表并设置服务器名称
                        return client.getTools().stream()
                                .peek(tool -> tool.setServerName(serverId));
                    } catch (Exception e) {
                        log.error("获取 MCP 服务器工具失败: {}", entry.getKey(), e);
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public List<MCPResource> getAllResources() {
        return clients.entrySet().stream()
                .flatMap(entry -> {
                    try {
                        String serverId = entry.getKey();
                        MCPClient client = entry.getValue();

                        // 检查服务器是否被禁用
                        McpServerSpec spec = specs.get(serverId);
                        if (spec != null && spec.isDisabled()) {
                            log.debug("服务器已禁用，跳过获取资源: {}", serverId);
                            return java.util.stream.Stream.empty();
                        }

                        // 检查连接状态
                        if (!client.isConnected()) {
                            log.warn("MCP 服务器连接已断开，跳过获取资源: {}", serverId);
                            return java.util.stream.Stream.empty();
                        }

                        // 获取资源列表并设置服务器名称
                        return client.getResources().stream()
                                .peek(resource -> resource.setServerName(serverId));
                    } catch (Exception e) {
                        log.error("获取 MCP 服务器资源失败: {}", entry.getKey(), e);
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public List<MCPPrompt> getAllPrompts() {
        return clients.entrySet().stream()
                .flatMap(entry -> {
                    try {
                        String serverId = entry.getKey();
                        MCPClient client = entry.getValue();

                        // 检查服务器是否被禁用
                        McpServerSpec spec = specs.get(serverId);
                        if (spec != null && spec.isDisabled()) {
                            log.debug("服务器已禁用，跳过获取提示模板: {}", serverId);
                            return java.util.stream.Stream.empty();
                        }

                        // 检查连接状态
                        if (!client.isConnected()) {
                            log.warn("MCP 服务器连接已断开，跳过获取提示模板: {}", serverId);
                            return java.util.stream.Stream.empty();
                        }

                        // 获取提示模板列表并设置服务器名称
                        return client.getPrompts().stream()
                                .peek(prompt -> prompt.setServerName(serverId));
                    } catch (Exception e) {
                        log.error("获取 MCP 服务器提示模板失败: {}", entry.getKey(), e);
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public List<McpServerSpec> getAllSpecs() {
        return List.copyOf(specs.values());
    }
    
    /**
     * 验证服务器配置
     */
    private void validateSpec(McpServerSpec spec) {
        if (spec.getId() == null || spec.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("服务器ID不能为空");
        }
        
        if (spec.getTransport() == null) {
            throw new IllegalArgumentException("传输协议不能为空");
        }
        
        // 根据传输协议验证必要参数
        switch (spec.getTransport()) {
            case STDIO:
                if (spec.getCommand() == null || spec.getCommand().trim().isEmpty()) {
                    throw new IllegalArgumentException("STDIO 传输协议需要指定命令");
                }
                break;
            case SSE:
            case STREAMABLEHTTP:
                if (spec.getUrl() == null || spec.getUrl().trim().isEmpty()) {
                    throw new IllegalArgumentException("HTTP 传输协议需要指定 URL");
                }
                break;
            default:
                throw new UnsupportedTransportException(spec.getTransport());
        }
    }
    
    /**
     * 根据配置创建 MCP 客户端
     */
    private MCPClient buildClient(McpServerSpec spec) {
        log.debug("创建 MCP 客户端，传输协议: {}, 服务器: {}", spec.getTransport(), spec.getId());
        
        return switch (spec.getTransport()) {
            case STDIO -> new MCPStdioClient(spec);
            case SSE -> new MCPSseClient(spec);
            case STREAMABLEHTTP -> new MCPStreamableHttpClient(spec);
            default -> throw new UnsupportedTransportException(spec.getTransport());
        };
    }
    
    /**
     * 获取服务器连接状态
     */
    public boolean isServerConnected(String serverId) {
        MCPClient client = clients.get(serverId);
        return client != null && client.isConnected();
    }
    
    /**
     * 重新连接服务器
     */
    public void reconnectServer(String serverId) {
        log.info("重新连接 MCP 服务器: {}", serverId);
        
        McpServerSpec spec = specs.get(serverId);
        if (spec == null) {
            throw new McpServerNotFoundException("服务器未找到: " + serverId);
        }
        
        // 关闭现有连接
        MCPClient oldClient = clients.get(serverId);
        if (oldClient != null) {
            try {
                oldClient.close();
            } catch (Exception e) {
                log.warn("关闭旧连接时发生错误: {}", serverId, e);
            }
        }
        
        // 创建新连接
        try {
            MCPClient newClient = buildClient(spec);
            clients.put(serverId, newClient);

            // 记录连接成功，重置失败计数
            retryManager.recordSuccess(serverId);
            log.info("重新连接 MCP 服务器成功: {}", serverId);
        } catch (Exception e) {
            // 记录连接失败
            retryManager.recordFailure(serverId);
            log.error("重新连接 MCP 服务器失败: {}", serverId, e);

            // 检查是否应该禁用服务器
            ConnectionRetryManager.FailureInfo failureInfo = retryManager.getFailureInfo(serverId);
            if (failureInfo != null && failureInfo.shouldGiveUp()) {
                log.warn("服务器 {} 重连连续失败 {} 次，自动禁用服务器", serverId, failureInfo.getFailureCount());
                disableServerAfterFailure(serverId);
            }

            throw new McpException("重新连接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 连接失败后禁用服务器
     */
    private void disableServerAfterFailure(String serverId) {
        try {
            McpServerSpec spec = specs.get(serverId);
            if (spec == null) {
                log.warn("无法禁用服务器，配置未找到: {}", serverId);
                return;
            }

            // 更新内存中的配置
            spec.setDisabled(true);
            specs.put(serverId, spec);

            // 更新数据库中的配置
            repository.save(spec);
            log.info("已在数据库中禁用服务器: {}", serverId);

            // 关闭现有连接
            MCPClient client = clients.remove(serverId);
            if (client != null) {
                try {
                    client.close();
                    log.debug("已关闭失败服务器的客户端连接: {}", serverId);
                } catch (Exception e) {
                    log.warn("关闭失败服务器连接时发生错误: {}", serverId, e);
                }
            }

            // 保存配置到文件
            try {
                configurationService.saveConfigurationToDefaultFile();
                log.info("已将禁用状态保存到配置文件: {}", serverId);
            } catch (Exception e) {
                log.warn("保存配置文件失败，但服务器已在数据库中禁用: {} - {}", serverId, e.getMessage());
            }

            // 清除失败信息，避免继续累积
            retryManager.clearFailureInfo(serverId);

        } catch (Exception e) {
            log.error("禁用失败服务器时发生错误: {}", serverId, e);
        }
    }
}