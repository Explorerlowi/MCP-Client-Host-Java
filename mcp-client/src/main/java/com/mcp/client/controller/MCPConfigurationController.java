package com.mcp.client.controller;

import com.mcp.client.service.MCPConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MCP 配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp/config")
@RequiredArgsConstructor
public class MCPConfigurationController {
    
    private final MCPConfigurationService configurationService;
    
    /**
     * 从 JSON 加载配置
     * @param configJson JSON 配置
     * @return 响应结果
     */
    @PostMapping("/load")
    public ResponseEntity<Map<String, Object>> loadConfiguration(@RequestBody String configJson) {
        try {
            log.info("接收到配置加载请求");
            int loadedCount = configurationService.loadConfigurationFromJson(configJson);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", String.format("配置加载成功，共导入 %d 个服务器", loadedCount),
                    "loadedCount", loadedCount
            ));

        } catch (Exception e) {
            log.error("配置加载失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "配置加载失败: " + e.getMessage()
            ));
        }
    }
    

    

    
    /**
     * 重新加载配置
     * @return 响应结果
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reloadConfiguration() {
        try {
            log.info("从数据库重新加载配置");
            int loadedCount = configurationService.loadConfigurationFromDatabase();

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", String.format("配置重新加载成功，共加载 %d 个服务器", loadedCount)
            ));

        } catch (Exception e) {
            log.error("配置重新加载失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "配置重新加载失败: " + e.getMessage()
            ));
        }
    }


}