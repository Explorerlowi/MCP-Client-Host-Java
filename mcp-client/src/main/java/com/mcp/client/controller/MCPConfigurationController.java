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
     * 从 JSON 导入配置
     * @param configJson JSON 配置
     * @return 响应结果
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importConfiguration(@RequestBody String configJson) {
        try {
            log.info("接收到 JSON 配置导入请求");
            int loadedCount = configurationService.loadConfigurationFromJson(configJson);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", String.format("配置导入成功，共导入 %d 个服务器", loadedCount),
                    "loadedCount", loadedCount
            ));

        } catch (Exception e) {
            log.error("JSON 配置导入失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "配置导入失败: " + e.getMessage()
            ));
        }
    }

}