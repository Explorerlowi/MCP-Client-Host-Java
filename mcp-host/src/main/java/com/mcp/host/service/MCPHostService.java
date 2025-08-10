package com.mcp.host.service;

import com.mcp.host.model.MCPInstruction;
import com.mcp.host.model.MCPToolResult;
import java.util.List;

/**
 * MCP 主机服务接口
 * 负责处理 MCP 指令执行和响应替换
 */
public interface MCPHostService {
    
    /**
     * 处理 MCP 指令列表
     * @param originalResponse 原始 LLM 响应
     * @param instructions MCP 指令列表
     * @return 处理后的响应
     */
    String processMCPInstructions(String originalResponse, List<MCPInstruction> instructions);
    
    /**
     * 执行单个 MCP 工具调用
     * @param instruction MCP 指令
     * @return 工具执行结果
     */
    MCPToolResult executeMCPTool(MCPInstruction instruction);
}