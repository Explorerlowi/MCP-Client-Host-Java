package com.mcp.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务器配置模型
 */
@Data
@Builder(toBuilder = true)
@Entity
@Table(name = "mcp_servers")
@NoArgsConstructor
@AllArgsConstructor
public class McpServerSpec {
    
    @Id
    private String id;
    
    private String name;
    
    private String description;
    
    @Enumerated(EnumType.STRING)
    private TransportType type;
    
    private String url;
    
    private String command;
    
    @Column(name = "args")
    private String args;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mcp_server_env", joinColumns = @JoinColumn(name = "server_id"))
    @MapKeyColumn(name = "env_key")
    @Column(name = "env_value")
    private Map<String, String> env;
    
    @Builder.Default
    private boolean disabled = false;
    
    /**
     * 请求超时时间（秒），默认60秒
     */
    @Builder.Default
    @Column(name = "timeout")
    private Long timeout = 60L;
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}