package com.mcp.host;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MCP Host 服务主应用类
 * 负责处理 LLM 通信和 JSON 指令解析
 */
@SpringBootApplication
@EnableScheduling
public class McpHostApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpHostApplication.class, args);
    }
}