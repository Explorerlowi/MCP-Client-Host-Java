package com.mcp.client.controller;

import com.mcp.client.model.McpServerSpec;
import com.mcp.client.model.MCPServerHealth;
import com.mcp.client.model.MCPTool;
import com.mcp.client.model.MCPResource;
import com.mcp.client.model.MCPPrompt;
import com.mcp.client.service.MCPServerRegistry;
import com.mcp.client.service.impl.MCPClientServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务器管理控制器
 * 提供 REST API 用于管理 MCP 服务器配置
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp/servers")
@CrossOrigin(origins = "*")
public class MCPServerController {
    
    @Autowired
    private MCPServerRegistry serverRegistry;
    
    @Autowired
    private MCPClientServiceImpl mcpClientService;
    
    /**
     * 获取所有服务器配置
     */
    @GetMapping
    public ResponseEntity<List<McpServerSpec>> listServers() {
        try {
            List<McpServerSpec> servers = serverRegistry.getAllSpecs();
            log.debug("返回 {} 个服务器配置", servers.size());
            return ResponseEntity.ok(servers);
        } catch (Exception e) {
            log.error("获取服务器列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 添加或更新服务器配置
     */
    @PostMapping
    public ResponseEntity<String> addOrUpdateServer(@RequestBody @Valid McpServerSpec spec) {
        try {
            log.info("添加/更新服务器配置: {}", spec.getId());
            serverRegistry.register(spec);
            return ResponseEntity.ok("服务器配置已保存: " + spec.getId());
        } catch (Exception e) {
            log.error("保存服务器配置失败: {}", spec.getId(), e);
            return ResponseEntity.badRequest()
                    .body("保存服务器配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除服务器配置
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteServer(@PathVariable String id) {
        try {
            log.info("删除服务器配置: {}", id);
            serverRegistry.unregister(id);
            return ResponseEntity.ok("服务器配置已删除: " + id);
        } catch (Exception e) {
            log.error("删除服务器配置失败: {}", id, e);
            return ResponseEntity.badRequest()
                    .body("删除服务器配置失败: " + e.getMessage());
        }
    }
    

    
    /**
     * 获取所有服务器健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<List<MCPServerHealth>> getAllServerHealth() {
        try {
            List<MCPServerHealth> healthList = mcpClientService.getAllServerHealth();
            return ResponseEntity.ok(healthList);
        } catch (Exception e) {
            log.error("获取所有服务器健康状态失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    

    
    /**
     * 重新连接服务器
     */
    @PostMapping("/{id}/reconnect")
    public ResponseEntity<String> reconnectServer(@PathVariable String id) {
        try {
            log.info("重新连接服务器: {}", id);

            // 获取服务器配置
            McpServerSpec spec = serverRegistry.getSpec(id);
            if (spec == null) {
                return ResponseEntity.badRequest().body("服务器不存在: " + id);
            }

            // 确保服务器是启用状态
            spec.setDisabled(false);

            // 重新注册服务器（这会重新建立连接并保存配置）
            serverRegistry.register(spec);

            return ResponseEntity.ok("服务器重新连接成功: " + id);
        } catch (Exception e) {
            log.error("重新连接服务器失败: {}", id, e);
            return ResponseEntity.badRequest()
                    .body("重新连接服务器失败: " + e.getMessage());
        }
    }

    /**
     * 关闭服务器连接
     */
    @PostMapping("/{id}/shutdown")
    public ResponseEntity<String> shutdownServer(@PathVariable String id) {
        try {
            log.info("关闭服务器连接: {}", id);

            // 获取服务器配置
            McpServerSpec spec = serverRegistry.getSpec(id);
            if (spec == null) {
                return ResponseEntity.badRequest().body("服务器不存在: " + id);
            }

            // 将服务器标记为禁用
            spec.setDisabled(true);

            // 重新注册服务器（这会关闭连接并保存配置）
            serverRegistry.register(spec);

            log.info("成功关闭服务器连接并更新配置: {}", id);
            return ResponseEntity.ok("服务器连接已关闭: " + id);
        } catch (Exception e) {
            log.error("关闭服务器连接失败: {}", id, e);
            return ResponseEntity.badRequest()
                    .body("关闭服务器连接失败: " + e.getMessage());
        }
    }




    
    /**
     * 获取连接统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<MCPClientServiceImpl.ServerConnectionStats> getConnectionStats() {
        try {
            MCPClientServiceImpl.ServerConnectionStats stats = mcpClientService.getConnectionStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取连接统计信息失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    


    /**
     * 获取指定服务器的工具列表
     */
    @GetMapping("/{id}/tools")
    public ResponseEntity<List<MCPTool>> getServerTools(@PathVariable String id) {
        try {
            log.info("获取服务器工具列表: {}", id);
            
            var client = serverRegistry.getClient(id);
            List<MCPTool> tools = client.getTools();
            
            // 确保每个工具都设置了服务器名称
            tools.forEach(tool -> {
                if (tool.getServerName() == null || tool.getServerName().isEmpty()) {
                    tool.setServerName(id);
                }
            });
            
            log.info("返回服务器 {} 的 {} 个工具", id, tools.size());
            return ResponseEntity.ok(tools);
        } catch (Exception e) {
            log.error("获取服务器工具列表失败: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }


    
    /**
     * 获取指定服务器的资源列表
     */
    @GetMapping("/{id}/resources")
    public ResponseEntity<List<MCPResource>> getServerResources(@PathVariable String id) {
        try {
            log.info("获取服务器资源列表: {}", id);
            
            var client = serverRegistry.getClient(id);
            List<MCPResource> resources = client.getResources();
            
            // 确保每个资源都设置了服务器名称
            resources.forEach(resource -> {
                if (resource.getServerName() == null || resource.getServerName().isEmpty()) {
                    resource.setServerName(id);
                }
            });
            
            log.info("返回服务器 {} 的 {} 个资源", id, resources.size());
            return ResponseEntity.ok(resources);
        } catch (Exception e) {
            log.error("获取服务器资源列表失败: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    

    
    /**
     * 获取指定服务器的提示模板列表
     */
    @GetMapping("/{id}/prompts")
    public ResponseEntity<List<MCPPrompt>> getServerPrompts(@PathVariable String id) {
        try {
            log.info("获取服务器提示模板列表: {}", id);
            
            var client = serverRegistry.getClient(id);
            List<MCPPrompt> prompts = client.getPrompts();
            
            // 确保每个提示模板都设置了服务器名称
            prompts.forEach(prompt -> {
                if (prompt.getServerName() == null || prompt.getServerName().isEmpty()) {
                    prompt.setServerName(id);
                }
            });
            
            log.info("返回服务器 {} 的 {} 个提示模板", id, prompts.size());
            return ResponseEntity.ok(prompts);
        } catch (Exception e) {
            log.error("获取服务器提示模板列表失败: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

}