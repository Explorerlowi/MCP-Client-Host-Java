package com.mcp.host.service;

import com.mcp.host.model.MCPToolInfo;
import java.util.List;

/**
 * MCP 系统提示构建器接口
 * 负责构建包含 MCP 工具信息的系统提示
 */
public interface MCPSystemPromptBuilder {
    
    /**
     * 构建包含 MCP 工具信息的系统提示
     * @return 系统提示字符串
     */
    String buildSystemPromptWithMCPTools();

    /**
     * 构建指定服务器集合的系统提示
     */
    String buildSystemPromptForServers(List<String> serverNames);

    /**
     * 获取可用工具列表
     * @return 工具信息列表
     */
    List<MCPToolInfo> getAvailableTools();
    
    /**
     * 获取指定服务器的工具列表
     * @param serverName 服务器名称
     * @return 工具信息列表
     */
    List<MCPToolInfo> getToolsForServer(String serverName);


}