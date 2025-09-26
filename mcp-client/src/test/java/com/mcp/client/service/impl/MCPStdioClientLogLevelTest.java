package com.mcp.client.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import com.mcp.client.model.McpServerSpec;

/**
 * 测试 MCPStdioClient 的日志级别判断逻辑
 */
public class MCPStdioClientLogLevelTest {

    private TestableMCPStdioClient client;

    /**
     * 可测试的 MCPStdioClient 子类，暴露私有方法用于测试
     */
    private static class TestableMCPStdioClient extends MCPStdioClient {
        public TestableMCPStdioClient(McpServerSpec spec) {
            super(spec, false); // 不自动初始化，避免启动进程
        }

        // 暴露私有方法用于测试
        public String testDetermineLogLevel(String line) {
            return super.determineLogLevel(line).toString();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // 创建一个测试用的 McpServerSpec
        McpServerSpec spec = new McpServerSpec();
        spec.setId("test-server");
        spec.setCommand("echo");

        // 创建可测试的客户端实例
        client = new TestableMCPStdioClient(spec);
    }
    
    @Test
    void testInfoLevelMessages() {
        // 测试应该被识别为 INFO 级别的消息
        String[] infoMessages = {
            "✅ MCP Web-Search Server 已启动（STDIO）",
            "Installed 52 packages in 865ms",
            "Server running on port 8080",
            "服务器启动成功",
            "MCP server started successfully",
            "Application running",
            "服务器已启动",
            "完成初始化"
        };

        for (String message : infoMessages) {
            String result = client.testDetermineLogLevel(message);
            assertEquals("INFO", result,
                "消息应该被识别为 INFO 级别: " + message);
        }
    }

    @Test
    void testWarnLevelMessages() {
        // 测试应该被识别为 WARN 级别的消息
        String[] warnMessages = {
            "npm warn Unknown builtin config \"ELECTRON_MIRROR\"",
            "Warning: deprecated function used",
            "WARN: Configuration not found",
            "警告：配置文件缺失"
        };

        for (String message : warnMessages) {
            String result = client.testDetermineLogLevel(message);
            assertEquals("WARN", result,
                "消息应该被识别为 WARN 级别: " + message);
        }
    }

    @Test
    void testErrorLevelMessages() {
        // 测试应该被识别为 ERROR 级别的消息
        String[] errorMessages = {
            "Error: Connection failed",
            "Exception in thread main",
            "Failed to start server",
            "Fatal error occurred",
            "Critical system failure"
        };

        for (String message : errorMessages) {
            String result = client.testDetermineLogLevel(message);
            assertEquals("ERROR", result,
                "消息应该被识别为 ERROR 级别: " + message);
        }
    }

    @Test
    void testDebugLevelMessages() {
        // 测试应该被识别为 DEBUG 级别的消息
        String[] debugMessages = {
            "Some random output",
            "Debug information",
            "",
            "   ",
            null,
            "Unrecognized message format"
        };

        for (String message : debugMessages) {
            String result = client.testDetermineLogLevel(message);
            assertEquals("DEBUG", result,
                "消息应该被识别为 DEBUG 级别: " + message);
        }
    }

    @Test
    void testPackageInstallationPattern() {
        // 测试包安装模式的识别
        String[] packageMessages = {
            "Installed 52 packages in 865ms",
            "Installed 1 package in 123ms",
            "Installed 999 packages in 5432ms"
        };

        for (String message : packageMessages) {
            String result = client.testDetermineLogLevel(message);
            assertEquals("INFO", result,
                "包安装消息应该被识别为 INFO 级别: " + message);
        }
    }

    @Test
    void demonstrateLogLevelClassification() {
        // 演示日志级别分类效果
        System.out.println("\n=== 日志级别分类演示 ===");

        String[] testMessages = {
            "✅ MCP Web-Search Server 已启动（STDIO）",
            "Installed 52 packages in 865ms",
            "npm warn Unknown builtin config \"ELECTRON_MIRROR\"",
            "Error: Connection failed",
            "Server running on port 8080",
            "警告：配置文件缺失",
            "Some random debug output",
            "Exception in thread main",
            "服务器启动成功",
            "Amap Maps MCP Server running on stdio"
        };

        for (String message : testMessages) {
            String level = client.testDetermineLogLevel(message);
            System.out.printf("%-6s | %s%n", level, message);
        }

        System.out.println("\n=== 说明 ===");
        System.out.println("INFO  - 正常的状态信息，如服务器启动、包安装等");
        System.out.println("WARN  - 警告信息，如配置问题、过时API等");
        System.out.println("ERROR - 错误信息，如连接失败、异常等");
        System.out.println("DEBUG - 其他调试信息，默认级别");
    }
}
