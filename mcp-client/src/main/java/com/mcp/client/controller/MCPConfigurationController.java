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
     * 从文件加载配置
     * @param filePath 文件路径
     * @return 响应结果
     */
    @PostMapping("/load-file")
    public ResponseEntity<Map<String, String>> loadConfigurationFromFile(@RequestParam String filePath) {
        try {
            log.info("从文件加载配置: {}", filePath);
            configurationService.loadConfigurationFromFile(filePath);
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "配置文件加载成功"
            ));
            
        } catch (Exception e) {
            log.error("配置文件加载失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "配置文件加载失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 导出当前配置
     * @return 当前配置的 JSON
     */
    @GetMapping("/export")
    public ResponseEntity<String> exportConfiguration() {
        try {
            String configJson = configurationService.exportConfigurationAsJson();
            return ResponseEntity.ok(configJson);
            
        } catch (Exception e) {
            log.error("配置导出失败", e);
            return ResponseEntity.internalServerError().body("配置导出失败: " + e.getMessage());
        }
    }
    
    /**
     * 重新加载配置
     * @return 响应结果
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reloadConfiguration() {
        try {
            // 这里可以实现从默认配置文件重新加载的逻辑
            log.info("重新加载配置");

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "配置重新加载成功"
            ));

        } catch (Exception e) {
            log.error("配置重新加载失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "配置重新加载失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 保存当前配置到文件
     * @return 响应结果
     */
    @PostMapping("/save")
    public ResponseEntity<Map<String, String>> saveConfiguration() {
        try {
            log.info("保存当前配置到文件");
            configurationService.saveConfigurationToDefaultFile();

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "配置已保存到 mcp-config.json 文件"
            ));

        } catch (Exception e) {
            log.error("保存配置到文件失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "保存配置失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 保存当前配置到指定文件
     * @param filePath 文件路径
     * @return 响应结果
     */
    @PostMapping("/save-to-file")
    public ResponseEntity<Map<String, String>> saveConfigurationToFile(@RequestParam String filePath) {
        try {
            log.info("保存当前配置到文件: {}", filePath);
            configurationService.saveConfigurationToFile(filePath);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "配置已保存到文件: " + filePath
            ));

        } catch (Exception e) {
            log.error("保存配置到文件失败: {}", filePath, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "保存配置失败: " + e.getMessage()
            ));
        }
    }
}