package com.mcp.client.config;

import com.mcp.client.service.impl.MCPClientServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * gRPC 服务器配置类
 * 负责启动和管理 gRPC 服务器
 */
@Slf4j
@Configuration
public class GrpcServerConfig {
    
    @Value("${grpc.server.port:9090}")
    private int grpcPort;
    
    @Autowired
    private MCPClientServiceImpl mcpClientService;
    
    private Server grpcServer;
    
    /**
     * 启动 gRPC 服务器
     */
    @PostConstruct
    public void startGrpcServer() {
        try {
            grpcServer = ServerBuilder.forPort(grpcPort)
                    .addService(mcpClientService)
                    .addService(ProtoReflectionService.newInstance())
                    .build();
            grpcServer.start();
            
            log.info("gRPC 服务器已启动，监听端口: {}", grpcPort);
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("收到关闭信号，正在关闭 gRPC 服务器...");
                stopGrpcServer();
            }));
            
        } catch (IOException e) {
            log.error("启动 gRPC 服务器失败", e);
            throw new RuntimeException("启动 gRPC 服务器失败", e);
        }
    }
    
    /**
     * 停止 gRPC 服务器
     */
    @PreDestroy
    @EventListener(ContextClosedEvent.class)
    public void stopGrpcServer() {
        if (grpcServer != null && !grpcServer.isShutdown()) {
            try {
                log.info("正在关闭 gRPC 服务器...");
                
                // 优雅关闭
                grpcServer.shutdown();
                
                // 等待服务器关闭
                if (!grpcServer.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("gRPC 服务器未在 10 秒内关闭，强制关闭");
                    grpcServer.shutdownNow();
                    
                    // 再次等待
                    if (!grpcServer.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("无法关闭 gRPC 服务器");
                    }
                }
                
                log.info("gRPC 服务器已关闭");
                
            } catch (InterruptedException e) {
                log.warn("等待 gRPC 服务器关闭时被中断");
                Thread.currentThread().interrupt();
                grpcServer.shutdownNow();
            }
        }
    }
    
    /**
     * 获取 gRPC 服务器状态
     */
    public boolean isServerRunning() {
        return grpcServer != null && !grpcServer.isShutdown() && !grpcServer.isTerminated();
    }
    
    /**
     * 获取 gRPC 服务器端口
     */
    public int getGrpcPort() {
        return grpcPort;
    }
    
    /**
     * 重启 gRPC 服务器
     */
    public void restartGrpcServer() {
        log.info("重启 gRPC 服务器...");
        
        stopGrpcServer();
        
        try {
            Thread.sleep(1000); // 等待 1 秒确保端口释放
            startGrpcServer();
            log.info("gRPC 服务器重启成功");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("重启 gRPC 服务器时被中断", e);
        }
    }
}