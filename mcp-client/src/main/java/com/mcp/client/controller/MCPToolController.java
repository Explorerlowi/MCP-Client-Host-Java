package com.mcp.client.controller;

import com.mcp.client.model.MCPTool;
import com.mcp.client.model.MCPToolResult;
import com.mcp.client.service.MCPServerRegistry;
import com.mcp.client.service.impl.MCPClientServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具调用 REST API 控制器
 * 提供与 gRPC 服务对应的 REST API 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp/tools")
@CrossOrigin(origins = "*")
public class MCPToolController {
    
    @Autowired
    private MCPServerRegistry serverRegistry;
    
    @Autowired
    private MCPClientServiceImpl mcpClientService;
    
    /**
     * 调用指定服务器的工具
     * 对应 gRPC 的 CallTool 方法
     */
    @PostMapping("/call")
    public ResponseEntity<MCPToolCallResponse> callTool(@RequestBody MCPToolCallRequest request) {
        try {
            log.info("收到工具调用请求 - 服务器: {}, 工具: {}, 参数: {}", 
                    request.getServerName(), request.getToolName(), request.getArguments());
            
            // 执行工具调用
            MCPToolResult result = serverRegistry.getClient(request.getServerName())
                    .callTool(request.getToolName(), request.getArguments());
            
            MCPToolCallResponse response = MCPToolCallResponse.builder()
                    .success(result.isSuccess())
                    .result(result.getResult())
                    .error(result.getError())
                    .build();
            
            log.info("工具调用完成 - 服务器: {}, 工具: {}, 成功: {}", 
                    request.getServerName(), request.getToolName(), result.isSuccess());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("工具调用失败 - 服务器: {}, 工具: {}", 
                    request.getServerName(), request.getToolName(), e);
            
            MCPToolCallResponse errorResponse = MCPToolCallResponse.builder()
                    .success(false)
                    .result("")
                    .error("工具调用失败: " + e.getMessage())
                    .build();
            
            return ResponseEntity.ok(errorResponse);
        }
    }
    
    /**
     * 获取指定服务器的工具列表
     * 对应 gRPC 的 GetTools 方法（指定服务器）
     */
    @GetMapping("/server/{serverName}")
    public ResponseEntity<List<MCPTool>> getServerTools(@PathVariable String serverName) {
        try {
            log.debug("收到获取工具列表请求 - 服务器: {}", serverName);
            
            List<MCPTool> tools = serverRegistry.getClient(serverName).getTools();
            
            log.info("返回工具列表 - 服务器: {}, 工具数量: {}", serverName, tools.size());
            
            return ResponseEntity.ok(tools);
            
        } catch (Exception e) {
            log.error("获取工具列表失败 - 服务器: {}", serverName, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 获取所有服务器的工具列表
     * 对应 gRPC 的 GetTools 方法（不指定服务器）
     */
    @GetMapping("/all")
    public ResponseEntity<List<MCPTool>> getAllTools() {
        try {
            log.debug("收到获取所有工具列表请求");
            
            List<MCPTool> tools = serverRegistry.getAllTools();
            
            log.info("返回所有工具列表 - 工具数量: {}", tools.size());
            
            return ResponseEntity.ok(tools);
            
        } catch (Exception e) {
            log.error("获取所有工具列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 工具调用请求 DTO
     */
    public static class MCPToolCallRequest {
        private String serverName;
        private String toolName;
        private Map<String, String> arguments;
        
        // Constructors
        public MCPToolCallRequest() {}
        
        public MCPToolCallRequest(String serverName, String toolName, Map<String, String> arguments) {
            this.serverName = serverName;
            this.toolName = toolName;
            this.arguments = arguments;
        }
        
        // Getters and Setters
        public String getServerName() { return serverName; }
        public void setServerName(String serverName) { this.serverName = serverName; }
        
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        
        public Map<String, String> getArguments() { return arguments; }
        public void setArguments(Map<String, String> arguments) { this.arguments = arguments; }
    }
    
    /**
     * 工具调用响应 DTO
     */
    public static class MCPToolCallResponse {
        private boolean success;
        private String result;
        private String error;
        
        // Constructors
        public MCPToolCallResponse() {}
        
        private MCPToolCallResponse(Builder builder) {
            this.success = builder.success;
            this.result = builder.result;
            this.error = builder.error;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public static class Builder {
            private boolean success;
            private String result;
            private String error;
            
            public Builder success(boolean success) {
                this.success = success;
                return this;
            }
            
            public Builder result(String result) {
                this.result = result;
                return this;
            }
            
            public Builder error(String error) {
                this.error = error;
                return this;
            }
            
            public MCPToolCallResponse build() {
                return new MCPToolCallResponse(this);
            }
        }
    }
}