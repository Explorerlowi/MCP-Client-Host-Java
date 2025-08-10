package com.mcp.client.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcp.client.exception.McpConnectionException;
import com.mcp.client.model.McpServerSpec;
import com.mcp.client.model.MCPTool;
import com.mcp.client.model.MCPToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * STDIO 传输协议的 MCP 客户端实现
 * 通过子进程的标准输入输出与 MCP 服务器通信
 */
@Slf4j
public class MCPStdioClient extends AbstractMCPClient {
    
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private BufferedReader errorReader;
    
    public MCPStdioClient(McpServerSpec spec) {
        super(spec);
        initializeProcess();
    }
    
    /**
     * 初始化子进程
     */
    private void initializeProcess() {
        try {
            // 安全地构建日志信息，避免懒加载异常
            String argsInfo = (spec.getArgs() != null && !spec.getArgs().isEmpty())
                ? String.join(" ", spec.getArgs())
                : "(无参数)";
            log.info("启动 MCP 服务器进程: {} {}", spec.getCommand(), argsInfo);

            ProcessBuilder pb = new ProcessBuilder();
            pb.command(spec.getCommand());

            if (spec.getArgs() != null && !spec.getArgs().isEmpty()) {
                pb.command().addAll(spec.getArgs());
            }
            
            if (spec.getEnv() != null && !spec.getEnv().isEmpty()) {
                pb.environment().putAll(spec.getEnv());
            }
            
            // 重定向错误流以便监控
            pb.redirectErrorStream(false);
            
            this.process = pb.start();
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
            this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            this.errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8));
            
            // 启动错误流监控线程
            startErrorStreamMonitor();
            
            // 执行初始化握手
            performHandshake();
            
            connected.set(true);
            log.info("MCP STDIO 客户端初始化成功: {}", spec.getId());
            
        } catch (Exception e) {
            log.error("初始化 MCP STDIO 客户端失败: {}", spec.getId(), e);
            cleanup();
            throw new McpConnectionException("初始化 STDIO 客户端失败", e);
        }
    }
    
    /**
     * 启动错误流监控线程
     */
    private void startErrorStreamMonitor() {
        Thread errorMonitor = new Thread(() -> {
            try {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    log.warn("MCP 服务器错误输出 [{}]: {}", spec.getId(), line);
                }
            } catch (IOException e) {
                log.debug("错误流监控结束: {}", spec.getId());
            }
        });
        errorMonitor.setDaemon(true);
        errorMonitor.setName("MCP-ErrorMonitor-" + spec.getId());
        errorMonitor.start();
    }

    /**
     * 读取JSON响应，跳过非JSON行（如Banner、日志等）
     * 这解决了某些MCP服务器在stdout输出Banner导致握手失败的问题
     */
    private String readJsonResponse() throws IOException {
        String line;
        int maxAttempts = 10; // 最多尝试读取10行，避免无限循环
        int attempts = 0;

        while ((line = reader.readLine()) != null && attempts < maxAttempts) {
            attempts++;
            line = line.trim();

            // 跳过空行
            if (line.isEmpty()) {
                continue;
            }

            // 检查是否是JSON格式
            if (isJsonLine(line)) {
                return line;
            }

            // 记录跳过的非JSON行（可能是Banner或日志）
            log.debug("跳过非JSON行 [{}]: {}", spec.getId(), line);
        }

        // 如果读取了maxAttempts行都不是JSON，返回最后一行让上层处理
        return line;
    }

    /**
     * 检查一行文本是否可能是JSON格式
     */
    private boolean isJsonLine(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }

        // JSON对象或数组的开始字符
        char firstChar = line.charAt(0);
        if (firstChar == '{' || firstChar == '[') {
            return true;
        }

        // JSON字符串、数字、布尔值或null
        if (firstChar == '"' || Character.isDigit(firstChar) || firstChar == '-' ||
            line.startsWith("true") || line.startsWith("false") || line.startsWith("null")) {
            return true;
        }

        // LSP/MCP协议中的Content-Length头（虽然STDIO通常不用，但保险起见）
        if (line.startsWith("Content-Length:")) {
            return true;
        }

        return false;
    }
    
    @Override
    protected void performHandshake() throws McpConnectionException {
        try {
            log.debug("开始 MCP 握手协议: {}", spec.getId());
            
            // 发送初始化请求
            JsonNode initRequest = buildInitializeRequest();
            JsonNode initResponse = sendRequest(initRequest);
            
            validateResponse(initResponse);
            
            // 解析服务器能力信息
            parseInitializeResponse(initResponse);
            
            log.debug("MCP 握手协议完成: {}", spec.getId());
            
        } catch (Exception e) {
            log.error("MCP 握手协议失败: {}", spec.getId(), e);
            throw new McpConnectionException("握手协议失败", e);
        }
    }
    
    @Override
    protected JsonNode sendRequest(JsonNode request) throws McpConnectionException {
        if (!isProcessAlive()) {
            throw new McpConnectionException("MCP 服务器进程已终止");
        }

        try {
            // 发送请求
            String requestStr = request.toString();
            log.debug("发送 MCP 请求 [{}]: {}", spec.getId(), requestStr);

            writer.write(requestStr);
            writer.newLine();
            writer.flush();

            // 读取响应 - 跳过非JSON行（如Banner、日志等）
            String responseStr = readJsonResponse();
            if (responseStr == null) {
                throw new McpConnectionException("服务器连接已关闭");
            }

            log.debug("收到 MCP 响应 [{}]: {}", spec.getId(), responseStr);

            return objectMapper.readTree(responseStr);

        } catch (IOException e) {
            log.error("发送 MCP 请求失败: {}", spec.getId(), e);
            connected.set(false);
            throw new McpConnectionException("发送请求失败", e);
        }
    }
    
    @Override
    public MCPToolResult callTool(String toolName, Map<String, String> arguments) {
        try {
            log.debug("调用 MCP 工具 [{}]: {} with args: {}", spec.getId(), toolName, arguments);
            
            JsonNode request = buildToolCallRequest(toolName, arguments);
            JsonNode response = sendRequest(request);
            
            MCPToolResult result = parseToolResult(response, toolName);
            log.debug("MCP 工具调用结果 [{}]: success={}", spec.getId(), result.isSuccess());
            
            return result;
            
        } catch (Exception e) {
            log.error("调用 MCP 工具失败 [{}]: {}", spec.getId(), toolName, e);
            return MCPToolResult.builder()
                    .success(false)
                    .error("工具调用失败: " + e.getMessage())
                    .toolName(toolName)
                    .serverName(spec.getId())
                    .build();
        }
    }
    
    @Override
    public List<MCPTool> getTools() {
        try {
            log.debug("获取 MCP 工具列表: {}", spec.getId());
            
            JsonNode request = buildListToolsRequest();
            JsonNode response = sendRequest(request);
            
            List<MCPTool> tools = parseToolsList(response);
            log.debug("获取到 {} 个 MCP 工具: {}", tools.size(), spec.getId());
            
            return tools;
            
        } catch (Exception e) {
            log.error("获取 MCP 工具列表失败: {}", spec.getId(), e);
            return List.of();
        }
    }
    
    @Override
    public void connect() {
        if (isConnected()) {
            log.debug("MCP STDIO 客户端已连接: {}", spec.getId());
            return;
        }

        log.info("连接 MCP STDIO 客户端: {}", spec.getId());
        initializeProcess();
    }

    @Override
    public void disconnect() {
        if (!isConnected()) {
            log.debug("MCP STDIO 客户端已断开: {}", spec.getId());
            return;
        }

        log.info("断开 MCP STDIO 客户端连接: {}", spec.getId());
        connected.set(false);

        // 优雅关闭进程
        if (process != null && process.isAlive()) {
            try {
                // 关闭输入流，通知服务器断开连接
                if (writer != null) {
                    writer.close();
                }

                // 等待进程自然结束
                boolean terminated = process.waitFor(5, TimeUnit.SECONDS);
                if (!terminated) {
                    log.warn("进程未在超时时间内结束，强制终止: {}", spec.getId());
                    process.destroyForcibly();
                }
            } catch (Exception e) {
                log.warn("断开连接时出现异常: {}", spec.getId(), e);
                process.destroyForcibly();
            }
        }
    }

    @Override
    public boolean isConnected() {
        return connected.get() && isProcessAlive();
    }

    /**
     * 检查进程是否存活
     */
    private boolean isProcessAlive() {
        return process != null && process.isAlive();
    }

    @Override
    public void close() {
        log.info("关闭 MCP STDIO 客户端: {}", spec.getId());
        disconnect();
        cleanup();
    }
    
    /**
     * 清理资源
     */
    private void cleanup() {
        // 关闭输入输出流
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            log.warn("关闭写入流失败: {}", spec.getId(), e);
        }
        
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            log.warn("关闭读取流失败: {}", spec.getId(), e);
        }
        
        try {
            if (errorReader != null) {
                errorReader.close();
            }
        } catch (IOException e) {
            log.warn("关闭错误流失败: {}", spec.getId(), e);
        }
        
        // 终止进程
        if (process != null) {
            try {
                // 优雅关闭
                process.destroy();
                
                // 等待进程结束
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    log.warn("进程未在 5 秒内结束，强制终止: {}", spec.getId());
                    process.destroyForcibly();
                }
                
                log.debug("MCP 服务器进程已终止: {}", spec.getId());
                
            } catch (InterruptedException e) {
                log.warn("等待进程结束时被中断: {}", spec.getId());
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}