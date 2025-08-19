package com.mcp.host.service.impl;

import com.mcp.grpc.MCPClientServiceGrpc;
import com.mcp.grpc.MCPToolRequest;
import com.mcp.grpc.MCPToolResponse;
import com.mcp.host.exception.McpException;
import com.mcp.host.model.MCPInstruction;
import com.mcp.host.model.MCPToolResult;
import com.mcp.host.service.MCPHostService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP 主机服务实现类
 * 负责处理 MCP 指令执行和响应替换
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MCPHostServiceImpl implements MCPHostService {
    
    private final MCPClientServiceGrpc.MCPClientServiceBlockingStub mcpClientStub;
    
    @Value("${mcp.client.grpc.timeout-seconds:120}")
    private int timeoutSeconds;
    
    @Override
    public String processMCPInstructions(String originalResponse, List<MCPInstruction> instructions) {
        if (instructions == null || instructions.isEmpty()) {
            log.debug("没有 MCP 指令需要处理");
            return originalResponse;
        }

        log.info("开始处理 {} 个 MCP 指令", instructions.size());
        StringBuilder processedResponse = new StringBuilder(originalResponse);

        // 如果原始响应不为空，添加分隔符
        if (originalResponse != null && !originalResponse.trim().isEmpty()) {
            processedResponse.append("\n\n---\n\n");
        }

        for (int i = 0; i < instructions.size(); i++) {
            MCPInstruction instruction = instructions.get(i);
            try {
                log.debug("执行 MCP 指令 {}/{}: {}", i + 1, instructions.size(), instruction);

                // 执行 MCP 工具调用
                MCPToolResult result = executeMCPTool(instruction);

                // 累积工具结果，而不是覆盖
                if (i > 0) {
                    processedResponse.append("\n\n---\n\n");
                }
                processedResponse.append(formatToolResult(result));

                log.debug("MCP 指令执行完成 {}/{}: {} -> {}",
                         i + 1, instructions.size(), instruction.getToolName(), result.isSuccess());

            } catch (Exception e) {
                log.error("执行 MCP 工具失败 {}/{}: {}", i + 1, instructions.size(), instruction, e);

                // 创建错误结果
                MCPToolResult errorResult = MCPToolResult.builder()
                        .success(false)
                        .error("工具执行失败: " + e.getMessage())
                        .toolName(instruction.getToolName())
                        .serverName(instruction.getServerName())
                        .timestamp(Instant.now())
                        .build();

                // 累积错误信息，而不是覆盖
                if (i > 0) {
                    processedResponse.append("\n\n---\n\n");
                }
                processedResponse.append(formatToolResult(errorResult));
            }
        }

        log.info("MCP 指令处理完成，共处理 {} 个指令", instructions.size());
        return processedResponse.toString();
    }
    
    @Override
    public MCPToolResult executeMCPTool(MCPInstruction instruction) {
        if (instruction == null) {
            throw new McpException("MCP 指令不能为空");
        }
        
        log.debug("执行 MCP 工具调用: 服务器={}, 工具={}", 
                instruction.getServerName(), instruction.getToolName());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 构建 gRPC 请求
            MCPToolRequest.Builder requestBuilder = MCPToolRequest.newBuilder()
                    .setServerName(instruction.getServerName())
                    .setToolName(instruction.getToolName());
            
            // 添加参数
            if (instruction.getArguments() != null) {
                requestBuilder.putAllArguments(instruction.getArguments());
            }
            
            MCPToolRequest request = requestBuilder.build();
            
            // 调用 MCP Client 服务
            log.debug("发送 gRPC 请求到 MCP Client 服务，超时时间: {}秒", timeoutSeconds);
            MCPToolResponse response = mcpClientStub
                    .withDeadlineAfter(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                    .callTool(request);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 构建结果
            MCPToolResult result = MCPToolResult.builder()
                    .success(response.getSuccess())
                    .result(response.getResult())
                    .error(response.getError())
                    .toolName(instruction.getToolName())
                    .serverName(instruction.getServerName())
                    .timestamp(Instant.now())
                    .executionTimeMs(executionTime)
                    .build();
            
            log.debug("MCP 工具调用完成: 成功={}, 耗时={}ms", result.isSuccess(), executionTime);
            return result;
            
        } catch (StatusRuntimeException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            String errorMessage = formatGrpcError(e);
            log.error("gRPC 调用失败: {}, 耗时={}ms", errorMessage, executionTime);
            
            return MCPToolResult.builder()
                    .success(false)
                    .error(errorMessage)
                    .toolName(instruction.getToolName())
                    .serverName(instruction.getServerName())
                    .timestamp(Instant.now())
                    .executionTimeMs(executionTime)
                    .build();
                    
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.error("MCP 工具调用出现未知错误", e);
            
            return MCPToolResult.builder()
                    .success(false)
                    .error("工具调用失败: " + e.getMessage())
                    .toolName(instruction.getToolName())
                    .serverName(instruction.getServerName())
                    .timestamp(Instant.now())
                    .executionTimeMs(executionTime)
                    .build();
        }
    }
    
    /**
     * 格式化工具执行结果
     */
    private String formatToolResult(MCPToolResult result) {
        if (result.isSuccess()) {
            return formatSuccessResult(result);
        } else {
            return formatErrorResult(result);
        }
    }
    
    /**
     * 格式化成功结果
     */
    private String formatSuccessResult(MCPToolResult result) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("**工具执行结果**\n\n");
        formatted.append("🔧 **工具**: ").append(result.getServerName()).append(":").append(result.getToolName()).append("\n");
        formatted.append("✅ **状态**: 执行成功\n");
        
        if (result.getExecutionTimeMs() != null) {
            formatted.append("⏱️ **耗时**: ").append(result.getExecutionTimeMs()).append("ms\n");
        }
        
        formatted.append("\n**结果**:\n");
        formatted.append("```\n");
        formatted.append(result.getResult() != null ? result.getResult() : "无返回内容");
        formatted.append("\n```");
        
        return formatted.toString();
    }
    
    /**
     * 格式化错误结果
     */
    private String formatErrorResult(MCPToolResult result) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("**工具执行结果**\n\n");
        formatted.append("🔧 **工具**: ").append(result.getServerName()).append(":").append(result.getToolName()).append("\n");
        formatted.append("❌ **状态**: 执行失败\n");
        
        if (result.getExecutionTimeMs() != null) {
            formatted.append("⏱️ **耗时**: ").append(result.getExecutionTimeMs()).append("ms\n");
        }
        
        formatted.append("\n**错误信息**:\n");
        formatted.append("```\n");
        formatted.append(result.getError() != null ? result.getError() : "未知错误");
        formatted.append("\n```");
        
        return formatted.toString();
    }
    
    /**
     * 格式化 gRPC 错误信息
     */
    private String formatGrpcError(StatusRuntimeException e) {
        Status status = e.getStatus();
        String description = status.getDescription();
        
        return switch (status.getCode()) {
            case UNAVAILABLE -> "MCP Client 服务不可用，请检查服务状态";
            case DEADLINE_EXCEEDED -> "工具调用超时";
            case NOT_FOUND -> "指定的 MCP 服务器或工具不存在";
            case INVALID_ARGUMENT -> "工具参数无效: " + (description != null ? description : "");
            case PERMISSION_DENIED -> "没有权限执行该工具";
            case INTERNAL -> "MCP Client 服务内部错误: " + (description != null ? description : "");
            default -> "gRPC 调用失败: " + status.getCode() + 
                      (description != null ? " - " + description : "");
        };
    }
}