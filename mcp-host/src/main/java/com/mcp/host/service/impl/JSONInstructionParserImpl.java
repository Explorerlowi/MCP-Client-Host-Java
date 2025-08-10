package com.mcp.host.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.host.exception.InstructionParseException;
import com.mcp.host.model.MCPInstruction;
import com.mcp.host.service.JSONInstructionParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON 指令解析器实现
 * 负责从 LLM 响应中提取和解析 MCP 工具调用指令
 */
@Slf4j
@Component
public class JSONInstructionParserImpl implements JSONInstructionParser {
    
    private final ObjectMapper objectMapper;
    
    // 匹配 JSON 代码块的正则表达式
    private static final Pattern JSON_CODE_BLOCK_PATTERN = 
        Pattern.compile("```json\\s*(\\{[^`]*?\\})\\s*```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    
    // MCP 指令类型常量
    private static final String MCP_INSTRUCTION_TYPE = "use_mcp_tool";
    
    @Autowired
    public JSONInstructionParserImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public List<MCPInstruction> parseInstructions(String llmResponse) {
        if (!StringUtils.hasText(llmResponse)) {
            log.debug("LLM 响应为空，跳过指令解析");
            return new ArrayList<>();
        }
        
        List<MCPInstruction> instructions = new ArrayList<>();
        
        try {
            // 使用正则表达式提取所有 JSON 代码块
            Matcher matcher = JSON_CODE_BLOCK_PATTERN.matcher(llmResponse);
            
            while (matcher.find()) {
                String jsonStr = matcher.group(1);
                log.debug("发现 JSON 代码块: {}", jsonStr);
                
                try {
                    // 解析 JSON 字符串
                    JsonNode jsonNode = objectMapper.readTree(jsonStr);
                    
                    // 检查是否为 MCP 工具调用指令
                    if (isMCPInstruction(jsonNode)) {
                        MCPInstruction instruction = parseMCPInstruction(jsonNode);
                        
                        // 验证指令格式
                        if (validateInstruction(instruction)) {
                            instructions.add(instruction);
                            log.info("成功解析 MCP 指令: 服务器={}, 工具={}", 
                                instruction.getServerName(), instruction.getToolName());
                        } else {
                            log.warn("MCP 指令验证失败: {}", jsonStr);
                        }
                    } else {
                        log.debug("JSON 不是 MCP 指令，跳过: {}", jsonStr);
                    }
                } catch (Exception e) {
                    log.warn("解析 JSON 指令失败: {}, 错误: {}", jsonStr, e.getMessage());
                    // 继续处理其他 JSON 块，不抛出异常
                }
            }
            
            log.info("共解析出 {} 个有效的 MCP 指令", instructions.size());
            return instructions;
            
        } catch (Exception e) {
            log.error("解析 LLM 响应中的指令时发生错误", e);
            throw new InstructionParseException("解析指令失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean validateInstruction(MCPInstruction instruction) {
        if (instruction == null) {
            log.debug("指令为 null");
            return false;
        }
        
        // 验证指令类型
        if (!MCP_INSTRUCTION_TYPE.equals(instruction.getType())) {
            log.debug("无效的指令类型: {}", instruction.getType());
            return false;
        }
        
        // 验证服务器名称
        if (!StringUtils.hasText(instruction.getServerName())) {
            log.debug("服务器名称为空");
            return false;
        }
        
        // 验证工具名称
        if (!StringUtils.hasText(instruction.getToolName())) {
            log.debug("工具名称为空");
            return false;
        }
        
        // 验证参数（可以为空 Map，但不能为 null）
        if (instruction.getArguments() == null) {
            log.debug("参数为 null");
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查 JSON 节点是否为 MCP 工具调用指令
     */
    private boolean isMCPInstruction(JsonNode jsonNode) {
        if (jsonNode == null || !jsonNode.isObject()) {
            return false;
        }
        
        // 检查是否包含必需的字段
        return jsonNode.has("type") && 
               jsonNode.has("server_name") && 
               jsonNode.has("tool_name") &&
               MCP_INSTRUCTION_TYPE.equals(jsonNode.get("type").asText());
    }
    
    /**
     * 解析 MCP 指令
     */
    private MCPInstruction parseMCPInstruction(JsonNode jsonNode) {
        try {
            String type = jsonNode.get("type").asText();
            String serverName = jsonNode.get("server_name").asText();
            String toolName = jsonNode.get("tool_name").asText();
            
            // 解析参数
            Map<String, String> arguments = parseArguments(jsonNode.get("arguments"));
            
            return MCPInstruction.builder()
                    .type(type)
                    .serverName(serverName)
                    .toolName(toolName)
                    .arguments(arguments)
                    .build();
                    
        } catch (Exception e) {
            throw new InstructionParseException("解析 MCP 指令失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析指令参数
     */
    private Map<String, String> parseArguments(JsonNode argumentsNode) {
        Map<String, String> arguments = new HashMap<>();

        if (argumentsNode == null || argumentsNode.isNull()) {
            return arguments;
        }

        if (!argumentsNode.isObject()) {
            log.warn("参数节点不是对象类型: {}", argumentsNode.getNodeType());
            return arguments;
        }

        // 特殊处理：如果参数中只有一个 "params" 字段且其值是对象，则展开该对象
        if (argumentsNode.size() == 1 && argumentsNode.has("params")) {
            JsonNode paramsNode = argumentsNode.get("params");
            if (paramsNode.isObject()) {
                log.debug("检测到嵌套的 params 对象，展开参数结构");
                return parseArgumentsFromNode(paramsNode);
            }
        }

        return parseArgumentsFromNode(argumentsNode);
    }

    /**
     * 从 JsonNode 解析参数
     */
    private Map<String, String> parseArgumentsFromNode(JsonNode node) {
        Map<String, String> arguments = new HashMap<>();

        // 遍历所有参数字段
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode valueNode = entry.getValue();

            // 将所有参数值转换为字符串
            String value;
            if (valueNode.isTextual()) {
                value = valueNode.asText();
            } else if (valueNode.isNumber()) {
                value = valueNode.asText();
            } else if (valueNode.isBoolean()) {
                value = String.valueOf(valueNode.asBoolean());
            } else if (valueNode.isNull()) {
                value = null;
            } else {
                // 对于复杂对象，转换为 JSON 字符串
                value = valueNode.toString();
            }

            arguments.put(key, value);
        });

        return arguments;
    }
}