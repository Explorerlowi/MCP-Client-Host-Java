package com.mcp.client.repository;

import com.mcp.client.model.McpServerSpec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * MCP 服务器配置数据访问接口
 */
@Repository
public interface McpServerRepository extends JpaRepository<McpServerSpec, String> {
    
    /**
     * 查找所有启用的服务器（未禁用的）
     * @return 启用的服务器列表
     */
    List<McpServerSpec> findByDisabledFalse();
    
    /**
     * 根据传输类型查找服务器
     * @param transport 传输类型
     * @return 服务器列表
     */
    List<McpServerSpec> findByTransport(com.mcp.client.model.TransportType transport);
    
    /**
     * 检查服务器ID是否存在
     * @param id 服务器ID
     * @return 是否存在
     */
    boolean existsById(String id);
    
    /**
     * 根据ID查找启用的服务器（未禁用的）
     * @param id 服务器ID
     * @return 服务器配置
     */
    @Query("SELECT s FROM McpServerSpec s WHERE s.id = :id AND s.disabled = false")
    Optional<McpServerSpec> findByIdAndDisabled(String id);
}