package com.mcp.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP Client 服务主应用类
 * 负责管理 MCP 服务器连接和工具调用
 */
@SpringBootApplication
public class McpClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpClientApplication.class, args);
    }
}