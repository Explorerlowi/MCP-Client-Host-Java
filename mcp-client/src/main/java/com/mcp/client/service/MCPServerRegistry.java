package com.mcp.client.service;

import com.mcp.client.model.McpServerSpec;
import com.mcp.client.model.MCPTool;
import com.mcp.client.model.MCPResource;
import com.mcp.client.model.MCPPrompt;
import java.util.List;

/**
 * MCP 服务器注册表接口
 * 负责管理 MCP 服务器的注册、配置和生命周期
 */
public interface MCPServerRegistry {
    
    /**
     * 注册 MCP 服务器
     * @param spec 服务器配置
     */
    void register(McpServerSpec spec);
    
    /**
     * 注销 MCP 服务器
     * @param serverId 服务器ID
     */
    void unregister(String serverId);
    
    /**
     * 注销所有 MCP 服务器
     */
    void unregisterAll();
    
    /**
     * 获取服务器配置
     * @param serverId 服务器ID
     * @return 服务器配置
     */
    McpServerSpec getSpec(String serverId);
    
    /**
     * 获取 MCP 客户端
     * @param serverId 服务器ID
     * @return MCP 客户端
     */
    MCPClient getClient(String serverId);

    /**
     * 获取现有的 MCP 客户端（不触发重新连接）
     * @param serverId 服务器ID
     * @return MCP 客户端，如果不存在则返回 null
     */
    MCPClient getExistingClient(String serverId);
    
    /**
     * 获取所有工具列表
     * @return 工具列表
     */
    List<MCPTool> getAllTools();
    
    /**
     * 获取所有资源列表
     * @return 资源列表
     */
    List<MCPResource> getAllResources();
    
    /**
     * 获取所有提示模板列表
     * @return 提示模板列表
     */
    List<MCPPrompt> getAllPrompts();
    
    /**
     * 获取所有服务器配置
     * @return 服务器配置列表
     */
    List<McpServerSpec> getAllSpecs();
}