package com.mcp.client.service.impl;

import com.mcp.client.model.McpServerSpec;

/**
 * 演示日志级别判断的效果
 */
public class LogLevelDemo {
    
    public static void main(String[] args) {
        // 创建测试客户端
        McpServerSpec spec = new McpServerSpec();
        spec.setId("demo-server");
        spec.setCommand("echo");
        
        TestMCPStdioClient client = new TestMCPStdioClient(spec);
        
        // 测试各种类型的消息
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
        
        System.out.println("=== 日志级别判断演示 ===");
        System.out.println();
        
        for (String message : testMessages) {
            String level = client.testDetermineLogLevel(message);
            System.out.printf("%-6s | %s%n", level, message);
        }
        
        System.out.println();
        System.out.println("=== 说明 ===");
        System.out.println("INFO  - 正常的状态信息，如服务器启动、包安装等");
        System.out.println("WARN  - 警告信息，如配置问题、过时API等");
        System.out.println("ERROR - 错误信息，如连接失败、异常等");
        System.out.println("DEBUG - 其他调试信息，默认级别");
    }
    
    /**
     * 测试用的 MCPStdioClient 子类
     */
    private static class TestMCPStdioClient extends MCPStdioClient {
        public TestMCPStdioClient(McpServerSpec spec) {
            super(spec, false); // 不自动初始化
        }
        
        public String testDetermineLogLevel(String line) {
            return super.determineLogLevel(line).toString();
        }
    }
}
