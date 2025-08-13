package com.mcp.host.service.impl;

import com.mcp.grpc.GetToolsRequest;
import com.mcp.grpc.GetToolsResponse;
import com.mcp.grpc.MCPClientServiceGrpc;
import com.mcp.grpc.MCPTool;
import com.mcp.host.exception.McpException;
import com.mcp.host.model.MCPToolInfo;
import com.mcp.host.service.MCPSystemPromptBuilder;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 系统提示构建器实现（精简版）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MCPSystemPromptBuilderImpl implements MCPSystemPromptBuilder {

    private final MCPClientServiceGrpc.MCPClientServiceBlockingStub mcpClientStub;
    
    @Value("${mcp.client.grpc.timeout-seconds:120}")
    private int timeoutSeconds;
    
    @Override
    public String buildSystemPromptWithMCPTools() {
        log.debug("构建包含 MCP 工具信息的系统提示");
        
        try {
            List<MCPToolInfo> tools = getAvailableTools();
            return buildPromptFromTools(tools);
        } catch (Exception e) {
            log.error("构建系统提示失败", e);
            return buildFallbackPrompt();
        }
    }
    
    @Override
    public String buildSystemPromptForServer(String serverName) {
        log.debug("构建服务器 {} 的系统提示", serverName);
        
        try {
            List<MCPToolInfo> tools = getToolsForServer(serverName);
            return buildPromptFromTools(tools);
        } catch (Exception e) {
            log.error("构建服务器 {} 的系统提示失败", serverName, e);
            return buildFallbackPrompt();
        }
    }
    
    @Override
    public String buildSystemPromptForServers(List<String> serverNames) {
        if (serverNames == null || serverNames.isEmpty()) {
            return buildSystemPromptWithMCPTools();
        }
        try {
            List<MCPToolInfo> tools = serverNames.stream()
                    .flatMap(name -> getToolsForServer(name).stream())
                    .collect(Collectors.toList());
            return buildPromptFromTools(tools);
        } catch (Exception e) {
            log.error("构建多服务器系统提示失败", e);
            return buildFallbackPrompt();
        }
    }
    
    @Override
    public List<MCPToolInfo> getAvailableTools() {
        log.debug("获取所有可用的 MCP 工具列表");

        try {
            GetToolsRequest request = GetToolsRequest.newBuilder().build();
            GetToolsResponse response = mcpClientStub
                    .withDeadlineAfter(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                    .getTools(request);

            List<MCPToolInfo> tools = response.getToolsList().stream()
                    .map(this::convertToToolInfo)
                    .collect(Collectors.toList());

            log.info("成功获取 {} 个 MCP 工具", tools.size());
            return tools;
        } catch (StatusRuntimeException e) {
            log.error("gRPC 调用失败: {}", e.getStatus(), e);
            throw new McpException("获取工具列表失败: " + e.getStatus().getDescription(), e);
        } catch (Exception e) {
            log.error("获取工具列表时发生未知错误", e);
            throw new McpException("获取工具列表失败", e);
        }
    }
    
    @Override
    public List<MCPToolInfo> getToolsForServer(String serverName) {
        log.debug("获取服务器 {} 的工具列表", serverName);

        try {
            GetToolsRequest request = GetToolsRequest.newBuilder()
                    .setServerName(serverName)
                    .build();
            GetToolsResponse response = mcpClientStub
                    .withDeadlineAfter(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                    .getTools(request);

            List<MCPToolInfo> tools = response.getToolsList().stream()
                    .map(this::convertToToolInfo)
                    .collect(Collectors.toList());

            log.info("成功获取服务器 {} 的 {} 个工具", serverName, tools.size());
            return tools;
        } catch (StatusRuntimeException e) {
            log.error("获取服务器 {} 工具列表的 gRPC 调用失败: {}", serverName, e.getStatus(), e);
            throw new McpException("获取服务器工具列表失败: " + e.getStatus().getDescription(), e);
        } catch (Exception e) {
            log.error("获取服务器 {} 工具列表时发生未知错误", serverName, e);
            throw new McpException("获取服务器工具列表失败", e);
        }
    }
    

    
    /**
     * 将 gRPC MCPTool 转换为 MCPToolInfo
     */
    private MCPToolInfo convertToToolInfo(MCPTool grpcTool) {
        return MCPToolInfo.builder()
                .name(grpcTool.getName())
                .description(grpcTool.getDescription())
                .serverName(grpcTool.getServerName())
                .inputSchema(grpcTool.getInputSchema())
                .outputSchema(grpcTool.getOutputSchema())
                .disabled(false)
                .build();
    }
    
    /**
     * 根据工具列表构建系统提示
     */
    private String buildPromptFromTools(List<MCPToolInfo> tools) {
        if (tools.isEmpty()) {
            return buildFallbackPrompt();
        }

        StringBuilder prompt = new StringBuilder();

        // 固定的介绍模板
        prompt.append("你是一个智能助手，可以使用以下工具来帮助用户：\n\n");

        // 按服务器分组显示工具
        Map<String, List<MCPToolInfo>> serverTools = tools.stream()
                .collect(Collectors.groupingBy(MCPToolInfo::getServerName));

        for (Map.Entry<String, List<MCPToolInfo>> entry : serverTools.entrySet()) {
            String serverName = entry.getKey();
            List<MCPToolInfo> serverToolList = entry.getValue();

            // 服务器组标题 - 明确标注服务器ID
            prompt.append(String.format("## %s 服务器的工具 (服务器ID: %s)\n", serverName, serverName));

            for (MCPToolInfo tool : serverToolList) {
                // 工具项
                prompt.append(String.format("- **%s**: %s\n", tool.getName(), tool.getDescription()));

                if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty()) {
                    // 参数格式
                    prompt.append(String.format("  参数格式: %s\n", tool.getInputSchema()));
                }
            }
            prompt.append("\n");
        }
        
        // 添加工具调用格式说明
        prompt.append("## 工具调用说明\n\n");
        prompt.append("""
                当需要使用工具时，请在响应中包含以下格式的 JSON 指令：

                ```json
                {
                    "type": "use_mcp_tool",
                    "server_name": "服务器ID",
                    "tool_name": "工具名称",
                    "arguments": {
                        "参数名": "参数值"
                    }
                }
                ```

                ## 重要提示

                1. 工具调用结果会自动替换 JSON 指令显示给用户
                2. **server_name 必须使用上述括号中标注的服务器ID，不是服务器标题**
                3. tool_name 必须与上述列表中的工具名称完全匹配
                4. arguments 中的参数必须符合工具的输入格式要求
                5. 如果工具调用失败，系统会显示错误信息

                现在请根据用户的需求，选择合适的工具来协助完成任务。
                """);

        return prompt.toString();
    }

    /**
     * 构建备用提示（当无法获取工具列表时使用）
     */
    private String buildFallbackPrompt() {
        return """
                你是一个智能助手。当前无法获取可用的工具列表，但你仍然可以：

                1. 回答用户的问题
                2. 提供建议和指导
                3. 进行对话交流

                如果用户需要使用特定工具，请告知他们当前工具服务不可用，建议稍后重试。
                """;
    }
}