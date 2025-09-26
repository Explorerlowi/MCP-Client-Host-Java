package com.mcp.client.service.impl;

import com.mcp.client.model.McpServerSpec;
import com.mcp.client.model.TransportType;
import com.mcp.client.repository.McpServerRepository;
import com.mcp.client.service.ConnectionRetryManager;
import com.mcp.client.service.MCPClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.*;

/**
 * 测试 MCPServerRegistryImpl 的关闭处理
 */
public class MCPServerRegistryShutdownTest {
    
    @Mock
    private McpServerRepository repository;
    
    @Mock
    private ConnectionRetryManager retryManager;
    
    @Mock
    private MCPClient mockClient1;
    
    @Mock
    private MCPClient mockClient2;
    
    private MCPServerRegistryImpl registry;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registry = new MCPServerRegistryImpl();
        
        // 注入依赖
        ReflectionTestUtils.setField(registry, "repository", repository);
        ReflectionTestUtils.setField(registry, "retryManager", retryManager);
        
        // 获取内部的 clients map 并添加测试客户端
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, MCPClient> clients = 
            (ConcurrentHashMap<String, MCPClient>) ReflectionTestUtils.getField(registry, "clients");
        
        clients.put("test-server-1", mockClient1);
        clients.put("test-server-2", mockClient2);
    }
    
    @Test
    void testCleanupClosesAllClients() {
        // 执行清理
        registry.cleanup();
        
        // 验证所有客户端都被关闭
        verify(mockClient1, times(1)).close();
        verify(mockClient2, times(1)).close();
        
        // 验证客户端映射被清空
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, MCPClient> clients = 
            (ConcurrentHashMap<String, MCPClient>) ReflectionTestUtils.getField(registry, "clients");
        
        assert clients.isEmpty() : "客户端映射应该被清空";
    }
    
    @Test
    void testCleanupHandlesClientCloseException() {
        // 模拟客户端关闭时抛出异常
        doThrow(new RuntimeException("关闭失败")).when(mockClient1).close();
        
        // 执行清理，不应该抛出异常
        registry.cleanup();
        
        // 验证即使有异常，其他客户端仍然被关闭
        verify(mockClient1, times(1)).close();
        verify(mockClient2, times(1)).close();
        
        // 验证客户端映射被清空
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, MCPClient> clients = 
            (ConcurrentHashMap<String, MCPClient>) ReflectionTestUtils.getField(registry, "clients");
        
        assert clients.isEmpty() : "即使有异常，客户端映射也应该被清空";
    }
}
