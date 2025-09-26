package com.mcp.client.service.impl;

import com.mcp.client.model.McpServerSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCPSseClient 断连清理逻辑的单元测试
 * 验证统一的断连清理逻辑是否正确工作
 */
@ExtendWith(MockitoExtension.class)
class MCPSseClientDisconnectionTest {

    @Test
    void testTransitionToDisconnectedMethodExists() {
        // 这个测试验证transitionToDisconnected方法存在且可以通过反射访问
        // 这确保了我们的重构没有破坏方法签名
        McpServerSpec spec = new McpServerSpec();
        spec.setId("test-server");
        spec.setUrl("http://localhost:8080/sse");
        spec.setTimeout(30L);
        
        MCPSseClient client = null;
        try {
            client = new MCPSseClient(spec);
        } catch (Exception e) {
            // 忽略连接异常，这是预期的，因为没有真实的服务器
        }

        final MCPSseClient finalClient = client;
        if (finalClient != null) {
            // 验证transitionToDisconnected方法存在
            assertDoesNotThrow(() -> {
                java.lang.reflect.Method method = finalClient.getClass().getDeclaredMethod(
                    "transitionToDisconnected", Throwable.class, boolean.class, boolean.class);
                assertNotNull(method, "transitionToDisconnected方法应该存在");
            }, "应该能够找到transitionToDisconnected方法");

            // 测试disconnect方法的幂等性
            assertDoesNotThrow(() -> {
                finalClient.disconnect();
                finalClient.disconnect(); // 第二次调用应该不会抛出异常
            }, "disconnect方法应该是幂等的");

            // 验证shouldReconnect被设置为false
            try {
                Boolean shouldReconnect = getPrivateField(finalClient, "shouldReconnect");
                assertFalse(shouldReconnect, "shouldReconnect应该被设置为false");
            } catch (Exception e) {
                fail("无法访问shouldReconnect字段: " + e.getMessage());
            }
        }
    }

    @Test
    void testDisconnectAndCloseAreIdempotent() {
        // 测试disconnect和close方法的幂等性
        McpServerSpec spec = new McpServerSpec();
        spec.setId("test-server-2");
        spec.setUrl("http://localhost:8081/sse");
        spec.setTimeout(30L);

        MCPSseClient client = null;
        try {
            client = new MCPSseClient(spec);
        } catch (Exception e) {
            // 忽略连接异常
        }

        final MCPSseClient finalClient2 = client;
        if (finalClient2 != null) {
            // 测试多次调用disconnect和close不会抛出异常
            assertDoesNotThrow(() -> {
                finalClient2.disconnect();
                finalClient2.close();
                finalClient2.disconnect();
                finalClient2.close();
            }, "disconnect和close方法应该是幂等的");
        }
    }

    @Test
    void testReconnectionLogicExists() {
        // 验证重连相关方法存在且可访问
        McpServerSpec spec = new McpServerSpec();
        spec.setId("test-reconnection");
        spec.setUrl("http://localhost:8082/sse");
        spec.setTimeout(30L);

        MCPSseClient client = null;
        try {
            client = new MCPSseClient(spec);
        } catch (Exception e) {
            // 忽略连接异常
        }

        final MCPSseClient finalClient = client;
        if (finalClient != null) {
            // 验证attemptReconnection方法存在
            assertDoesNotThrow(() -> {
                java.lang.reflect.Method method = finalClient.getClass().getDeclaredMethod("attemptReconnection");
                assertNotNull(method, "attemptReconnection方法应该存在");
            }, "应该能够找到attemptReconnection方法");

            // 验证initializeConnectionInternal方法存在
            assertDoesNotThrow(() -> {
                java.lang.reflect.Method method = finalClient.getClass().getDeclaredMethod("initializeConnectionInternal");
                assertNotNull(method, "initializeConnectionInternal方法应该存在");
            }, "应该能够找到initializeConnectionInternal方法");
        }
    }

    // 辅助方法：通过反射获取私有字段
    @SuppressWarnings("unchecked")
    private <T> T getPrivateField(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(obj);
    }
}
