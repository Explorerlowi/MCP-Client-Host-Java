# MCP 服务器错误流日志级别改进

## 问题描述

在原始实现中，`MCPStdioClient` 将所有从 MCP 服务器错误流（stderr）读取到的内容都以 `WARN` 级别记录为"MCP 服务器错误输出"。这导致了以下问题：

1. **正常状态信息被误标记为错误**：
   - `✅ MCP Web-Search Server 已启动（STDIO）` - 服务器启动成功信息
   - `Installed 52 packages in 865ms` - npm/uvx 包安装进度信息
   - `Amap Maps MCP Server running on stdio` - 服务器运行状态信息

2. **日志噪音过多**：所有 stderr 输出都被标记为警告，增加了日志的噪音，影响了真正重要信息的识别。

3. **缺乏日志级别区分**：无法区分真正的错误、警告和正常状态信息。

## 解决方案

### 1. 智能日志级别判断

实现了 `determineLogLevel()` 方法，根据输出内容的关键词和模式自动判断合适的日志级别：

- **ERROR 级别**：包含 "error", "exception", "failed", "failure", "fatal", "critical" 等关键词
- **WARN 级别**：包含 "warn", "warning", "deprecated", "警告" 等关键词  
- **INFO 级别**：包含启动、安装、运行等正常状态信息的关键词和模式
- **DEBUG 级别**：其他未识别的内容，默认级别

### 2. 多语言支持

支持中英文关键词识别：
- 英文：started, running, server, installed, packages, warn, error 等
- 中文：启动, 运行, 服务器, 警告, 错误, 成功, 完成 等

### 3. 模式匹配

支持特定模式的识别：
- 包安装模式：`Installed X packages in Yms`
- 服务器运行模式：`Server running on ...`
- 启动成功模式：包含 ✅ 符号的消息

## 代码修改

### 主要修改文件

1. **MCPStdioClient.java**
   - 修改 `startErrorStreamMonitor()` 方法
   - 新增 `LogLevel` 枚举
   - 新增 `determineLogLevel()` 方法
   - 新增受保护的构造函数用于测试

### 关键代码片段

```java
/**
 * 根据输出内容确定合适的日志级别
 */
protected LogLevel determineLogLevel(String line) {
    if (line == null || line.trim().isEmpty()) {
        return LogLevel.DEBUG;
    }
    
    String lowerLine = line.toLowerCase().trim();
    
    // 错误级别的关键词
    if (lowerLine.contains("error") || 
        lowerLine.contains("exception") || 
        lowerLine.contains("failed") || 
        lowerLine.contains("failure") ||
        lowerLine.contains("fatal") ||
        lowerLine.contains("critical")) {
        return LogLevel.ERROR;
    }
    
    // 警告级别的关键词
    if (lowerLine.contains("warn") ||
        lowerLine.contains("warning") ||
        lowerLine.contains("deprecated") ||
        lowerLine.contains("警告") ||
        line.contains("警告")) {
        return LogLevel.WARN;
    }
    
    // 正常信息级别的关键词和模式
    if (lowerLine.contains("started") ||
        lowerLine.contains("启动") ||
        lowerLine.contains("running") ||
        lowerLine.contains("server") ||
        lowerLine.contains("installed") ||
        lowerLine.contains("packages") ||
        lowerLine.contains("✅") ||
        lowerLine.contains("成功") ||
        lowerLine.contains("完成") ||
        lowerLine.matches(".*\\d+.*packages.*in.*\\d+.*ms.*") ||
        lowerLine.matches(".*server.*running.*") ||
        lowerLine.matches(".*已启动.*")) {
        return LogLevel.INFO;
    }
    
    // 默认为调试级别，避免过多噪音
    return LogLevel.DEBUG;
}
```

## 效果演示

修改后的日志级别分类效果：

```
INFO   | ✅ MCP Web-Search Server 已启动（STDIO）
INFO   | Installed 52 packages in 865ms
WARN   | npm warn Unknown builtin config "ELECTRON_MIRROR"
ERROR  | Error: Connection failed
INFO   | Server running on port 8080
WARN   | 警告：配置文件缺失
DEBUG  | Some random debug output
ERROR  | Exception in thread main
INFO   | 服务器启动成功
INFO   | Amap Maps MCP Server running on stdio
```

## 测试覆盖

创建了完整的单元测试 `MCPStdioClientLogLevelTest`，覆盖：
- INFO 级别消息识别
- WARN 级别消息识别  
- ERROR 级别消息识别
- DEBUG 级别消息识别
- 包安装模式识别
- 中英文关键词识别

## 向后兼容性

- 保持了原有的错误流监控功能
- 只是改进了日志级别的判断逻辑
- 不影响现有的 MCP 服务器通信协议
- 对外接口保持不变

## 优势

1. **减少日志噪音**：正常状态信息不再被标记为警告
2. **提高可读性**：不同类型的信息使用合适的日志级别
3. **便于调试**：真正的错误和警告更容易识别
4. **多语言支持**：支持中英文环境
5. **可扩展性**：易于添加新的关键词和模式

## 使用建议

1. 在生产环境中，可以将 DEBUG 级别的日志设置为不输出，减少日志量
2. 重点关注 ERROR 和 WARN 级别的日志
3. INFO 级别的日志可以用于监控服务器状态
4. 如需添加新的关键词识别，可以扩展 `determineLogLevel()` 方法
