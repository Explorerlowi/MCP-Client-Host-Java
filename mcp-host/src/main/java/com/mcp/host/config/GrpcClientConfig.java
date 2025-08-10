package com.mcp.host.config;

import com.mcp.grpc.MCPClientServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * gRPC 客户端配置（精简版）
 * 负责配置与 MCP Client 服务的 gRPC 连接
 */
@Slf4j
@Configuration
public class GrpcClientConfig {

    @Value("${mcp.client.grpc.host:localhost}")
    private String grpcHost;

    @Value("${mcp.client.grpc.port:9090}")
    private int grpcPort;

    @Value("${mcp.client.grpc.timeout-seconds:120}")
    private int timeoutSeconds;

    private ManagedChannel channel;

    /**
     * 创建 gRPC 客户端连接通道
     */
    @Bean
    public ManagedChannel mcpClientChannel() {
        log.info("创建 gRPC 客户端连接: {}:{}", grpcHost, grpcPort);

        ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forAddress(grpcHost, grpcPort)
                .usePlaintext() // 在开发环境中使用明文连接
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .maxInboundMessageSize(4 * 1024 * 1024) // 4MB
                .maxInboundMetadataSize(8192); // 8KB 元数据大小限制

        this.channel = channelBuilder.build();

        return this.channel;
    }

    /**
     * 创建阻塞式 gRPC 客户端存根
     */
    @Bean
    public MCPClientServiceGrpc.MCPClientServiceBlockingStub mcpClientStub(ManagedChannel channel) {
        log.info("创建 MCP Client 服务 gRPC 阻塞存根，调用超时: {}秒", timeoutSeconds);

        return MCPClientServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * 创建异步 gRPC 客户端存根
     */
    @Bean
    public MCPClientServiceGrpc.MCPClientServiceStub mcpClientAsyncStub(ManagedChannel channel) {
        log.info("创建 MCP Client 服务 gRPC 异步存根，调用超时: {}秒", timeoutSeconds);

        return MCPClientServiceGrpc.newStub(channel)
                .withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * 创建 Future 风格的 gRPC 客户端存根
     */
    @Bean
    public MCPClientServiceGrpc.MCPClientServiceFutureStub mcpClientFutureStub(ManagedChannel channel) {
        log.info("创建 MCP Client 服务 gRPC Future 存根，调用超时: {}秒", timeoutSeconds);

        return MCPClientServiceGrpc.newFutureStub(channel)
                .withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * 获取 gRPC 连接状态
     */
    public boolean isChannelReady() {
        return channel != null && !channel.isShutdown() && !channel.isTerminated();
    }
    
    /**
     * 获取连接状态信息
     */
    public String getChannelState() {
        if (channel == null) {
            return "NOT_INITIALIZED";
        }
        return channel.getState(false).toString();
    }
    
    /**
     * 清理资源
     */
    @PreDestroy
    public void cleanup() {
        if (channel != null && !channel.isShutdown()) {
            log.info("关闭 gRPC 客户端连接");
            channel.shutdown();
            
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("gRPC 客户端连接未能在5秒内正常关闭，强制关闭");
                    channel.shutdownNow();
                    
                    if (!channel.awaitTermination(2, TimeUnit.SECONDS)) {
                        log.error("无法强制关闭 gRPC 客户端连接");
                    }
                }
                log.info("gRPC 客户端连接已关闭");
            } catch (InterruptedException e) {
                log.error("等待 gRPC 客户端关闭时被中断", e);
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}