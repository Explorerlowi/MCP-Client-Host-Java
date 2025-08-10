package com.mcp.client.service;

import com.mcp.client.model.MCPTool;
import com.mcp.client.model.MCPToolResult;
import com.mcp.client.model.MCPServerInfo;
import com.mcp.client.model.MCPResource;
import com.mcp.client.model.MCPPrompt;
import java.util.List;
import java.util.Map;

/**
 * MCP 客户端接口
 * 定义与 MCP 服务器通信的基本方法
 */
public interface MCPClient {
    
    /**
     * 检查客户端是否已连接
     * @return 连接状态
     */
    boolean isConnected();
    
    /**
     * 连接到 MCP 服务器
     */
    void connect();
    
    /**
     * 断开与 MCP 服务器的连接
     */
    void disconnect();
    
    /**
     * 关闭客户端并释放资源
     */
    void close();
    
    /**
     * 获取服务器提供的工具列表
     * @return 工具列表
     */
    List<MCPTool> getTools();
    
    /**
     * 调用 MCP 工具
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @return 工具执行结果
     */
    MCPToolResult callTool(String toolName, Map<String, String> arguments);
    
    /**
     * 获取服务器信息
     * @return 服务器信息
     */
    MCPServerInfo getServerInfo();
    
    /**
     * 获取服务器提供的资源列表
     * @return 资源列表
     */
    default List<MCPResource> getResources() {
        return List.of();
    }
    
    /**
     * 读取指定资源的内容
     * @param uri 资源URI
     * @return 资源内容
     */
    default String readResource(String uri) {
        throw new UnsupportedOperationException("服务器不支持资源功能");
    }
    
    /**
     * 获取服务器提供的提示模板列表
     * @return 提示模板列表
     */
    default List<MCPPrompt> getPrompts() {
        return List.of();
    }
    
    /**
     * 生成提示内容
     * @param promptId 提示模板ID
     * @param arguments 模板参数
     * @return 生成的提示内容
     */
    default String generatePrompt(String promptId, Map<String, String> arguments) {
        throw new UnsupportedOperationException("服务器不支持提示模板功能");
    }
    
    /**
     * 获取完整的提示对象
     * @param promptName 提示模板名称
     * @param arguments 模板参数
     * @return 完整的提示对象
     */
    default MCPPrompt getPrompt(String promptName, Map<String, String> arguments) {
        throw new UnsupportedOperationException("服务器不支持提示模板功能");
    }
}