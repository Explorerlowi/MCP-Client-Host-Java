package com.mcp.host.service;

import com.mcp.host.model.MCPInstruction;
import java.util.List;

/**
 * JSON 指令解析器接口
 * 负责从 LLM 响应中提取和解析 MCP 工具调用指令
 */
public interface JSONInstructionParser {
    
    /**
     * 解析 LLM 响应中的 MCP 指令
     * @param llmResponse LLM 响应内容
     * @return MCP 指令列表
     */
    List<MCPInstruction> parseInstructions(String llmResponse);
    
    /**
     * 验证指令格式
     * @param instruction MCP 指令
     * @return 是否有效
     */
    boolean validateInstruction(MCPInstruction instruction);
}