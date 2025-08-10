package com.mcp.client.service.impl;

import com.mcp.client.exception.McpServerNotFoundException;
import com.mcp.client.model.MCPServerHealth;
import com.mcp.client.model.MCPTool;
import com.mcp.client.model.MCPToolResult;
import com.mcp.client.service.MCPClient;
import com.mcp.client.service.MCPServerRegistry;
import com.mcp.grpc.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * MCP 客户端 gRPC 服务实现
 * 处理来自 MCP Host 的 gRPC 调用
 */
@Slf4j
@Service
public class MCPClientServiceImpl extends MCPClientServiceGrpc.MCPClientServiceImplBase {
    
    @Autowired
    private MCPServerRegistry serverRegistry;
    
    @Override
    public void callTool(MCPToolRequest request, StreamObserver<MCPToolResponse> responseObserver) {
        try {
            String serverName = request.getServerName();
            String toolName = request.getToolName();
            Map<String, String> arguments = request.getArgumentsMap();
            
            log.info("收到工具调用请求 - 服务器: {}, 工具: {}, 参数: {}", serverName, toolName, arguments);
            
            // 获取对应的 MCP 客户端
            MCPClient client = serverRegistry.getClient(serverName);
            
            // 执行工具调用
            MCPToolResult result = client.callTool(toolName, arguments);
            
            // 构建响应
            MCPToolResponse response = MCPToolResponse.newBuilder()
                    .setSuccess(result.isSuccess())
                    .setResult(result.getResult() != null ? result.getResult() : "")
                    .setError(result.getError() != null ? result.getError() : "")
                    .build();
            
            log.info("工具调用完成 - 服务器: {}, 工具: {}, 成功: {}", 
                    serverName, toolName, result.isSuccess());
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (McpServerNotFoundException e) {
            log.error("服务器未找到: {}", request.getServerName());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("MCP 服务器未找到: " + request.getServerName())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("工具调用失败 - 服务器: {}, 工具: {}", 
                    request.getServerName(), request.getToolName(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("工具调用失败: " + e.getMessage())
                    .asRuntimeException());
        }
    }
    
    @Override
    public void getTools(GetToolsRequest request, StreamObserver<GetToolsResponse> responseObserver) {
        try {
            String serverName = request.getServerName();
            
            log.debug("收到获取工具列表请求 - 服务器: {}", 
                    serverName.isEmpty() ? "全部" : serverName);
            
            List<MCPTool> tools;
            
            if (serverName != null && !serverName.trim().isEmpty()) {
                // 获取指定服务器的工具
                MCPClient client = serverRegistry.getClient(serverName);
                tools = client.getTools();
            } else {
                // 获取所有服务器的工具
                tools = serverRegistry.getAllTools();
            }
            
            // 转换为 gRPC 消息格式
            GetToolsResponse.Builder responseBuilder = GetToolsResponse.newBuilder();
            
            for (MCPTool tool : tools) {
                com.mcp.grpc.MCPTool grpcTool = com.mcp.grpc.MCPTool.newBuilder()
                        .setName(tool.getName())
                        .setDescription(tool.getDescription() != null ? tool.getDescription() : "")
                        .setServerName(tool.getServerName())
                        .setInputSchema(tool.getInputSchema() != null ? tool.getInputSchema() : "{}")
                        .setOutputSchema(tool.getOutputSchema() != null ? tool.getOutputSchema() : "{}")
                        .build();
                
                responseBuilder.addTools(grpcTool);
            }
            
            GetToolsResponse response = responseBuilder.build();
            
            log.info("返回工具列表 - 服务器: {}, 工具数量: {}", 
                    serverName.isEmpty() ? "全部" : serverName, tools.size());
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (McpServerNotFoundException e) {
            log.error("服务器未找到: {}", request.getServerName());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("MCP 服务器未找到: " + request.getServerName())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("获取工具列表失败 - 服务器: {}", request.getServerName(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("获取工具列表失败: " + e.getMessage())
                    .asRuntimeException());
        }
    }
    
    @Override
    public void getServerHealth(GetServerHealthRequest request, 
                               StreamObserver<GetServerHealthResponse> responseObserver) {
        try {
            String serverName = request.getServerName();
            
            log.debug("收到健康检查请求 - 服务器: {}", serverName);
            
            // 检查服务器是否存在
            MCPClient client = serverRegistry.getClient(serverName);
            
            // 获取连接状态
            boolean connected = client.isConnected();
            String status = connected ? "HEALTHY" : "UNHEALTHY";
            
            // 构建响应
            GetServerHealthResponse response = GetServerHealthResponse.newBuilder()
                    .setServerName(serverName)
                    .setConnected(connected)
                    .setStatus(status)
                    .setLastCheckTime(Instant.now().toEpochMilli())
                    .build();
            
            log.debug("服务器健康检查结果 - 服务器: {}, 状态: {}", serverName, status);
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (McpServerNotFoundException e) {
            log.error("服务器未找到: {}", request.getServerName());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("MCP 服务器未找到: " + request.getServerName())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("健康检查失败 - 服务器: {}", request.getServerName(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("健康检查失败: " + e.getMessage())
                    .asRuntimeException());
        }
    }
    
    /**
     * 获取所有服务器的健康状态
     */
    public List<MCPServerHealth> getAllServerHealth() {
        return serverRegistry.getAllSpecs().stream()
                .map(spec -> {
                    try {
                        // 直接检查现有客户端状态，不触发重新连接
                        MCPClient client = serverRegistry.getExistingClient(spec.getId());
                        boolean connected = client != null && client.isConnected();

                        String status;
                        String errorMessage = null;

                        if (connected) {
                            status = "HEALTHY";
                        } else if (client == null) {
                            status = "NOT_CONNECTED";
                        } else {
                            status = "UNHEALTHY";
                        }

                        return MCPServerHealth.builder()
                                .serverId(spec.getId())
                                .connected(connected)
                                .status(status)
                                .lastCheck(Instant.now())
                                .errorMessage(errorMessage)
                                .build();

                    } catch (Exception e) {
                        log.debug("获取服务器健康状态时出现异常: {} - {}", spec.getId(), e.getMessage());
                        return MCPServerHealth.builder()
                                .serverId(spec.getId())
                                .connected(false)
                                .status("ERROR")
                                .lastCheck(Instant.now())
                                .errorMessage(e.getMessage())
                                .build();
                    }
                })
                .toList();
    }
    
    /**
     * 重新连接指定服务器
     */
    public void reconnectServer(String serverId) {
        try {
            log.info("重新连接服务器: {}", serverId);
            
            if (serverRegistry instanceof MCPServerRegistryImpl registryImpl) {
                registryImpl.reconnectServer(serverId);
                log.info("服务器重新连接成功: {}", serverId);
            } else {
                throw new UnsupportedOperationException("当前注册表实现不支持重新连接操作");
            }
        } catch (Exception e) {
            log.error("重新连接服务器失败: {}", serverId, e);
            throw e;
        }
    }
    
    /**
     * 获取服务器连接统计信息
     */
    public ServerConnectionStats getConnectionStats() {
        List<MCPServerHealth> healthList = getAllServerHealth();
        
        long totalServers = healthList.size();
        long connectedServers = healthList.stream()
                .mapToLong(health -> health.isConnected() ? 1 : 0)
                .sum();
        long disconnectedServers = totalServers - connectedServers;
        
        return ServerConnectionStats.builder()
                .totalServers(totalServers)
                .connectedServers(connectedServers)
                .disconnectedServers(disconnectedServers)
                .healthCheckTime(Instant.now())
                .build();
    }
    
    /**
     * 服务器连接统计信息
     */
    public static class ServerConnectionStats {
        private final long totalServers;
        private final long connectedServers;
        private final long disconnectedServers;
        private final Instant healthCheckTime;
        
        private ServerConnectionStats(Builder builder) {
            this.totalServers = builder.totalServers;
            this.connectedServers = builder.connectedServers;
            this.disconnectedServers = builder.disconnectedServers;
            this.healthCheckTime = builder.healthCheckTime;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public long getTotalServers() { return totalServers; }
        public long getConnectedServers() { return connectedServers; }
        public long getDisconnectedServers() { return disconnectedServers; }
        public Instant getHealthCheckTime() { return healthCheckTime; }
        
        public static class Builder {
            private long totalServers;
            private long connectedServers;
            private long disconnectedServers;
            private Instant healthCheckTime;
            
            public Builder totalServers(long totalServers) {
                this.totalServers = totalServers;
                return this;
            }
            
            public Builder connectedServers(long connectedServers) {
                this.connectedServers = connectedServers;
                return this;
            }
            
            public Builder disconnectedServers(long disconnectedServers) {
                this.disconnectedServers = disconnectedServers;
                return this;
            }
            
            public Builder healthCheckTime(Instant healthCheckTime) {
                this.healthCheckTime = healthCheckTime;
                return this;
            }
            
            public ServerConnectionStats build() {
                return new ServerConnectionStats(this);
            }
        }
    }
}