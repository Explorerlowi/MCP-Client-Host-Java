-- MCP Client 数据库初始化脚本

-- 创建 MCP 服务器配置表
CREATE TABLE IF NOT EXISTS mcp_servers (
    id VARCHAR(255) PRIMARY KEY,
    transport VARCHAR(50) NOT NULL,
    url VARCHAR(500),
    command VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建服务器参数表
CREATE TABLE IF NOT EXISTS mcp_server_args (
    server_id VARCHAR(255) NOT NULL,
    arg VARCHAR(500) NOT NULL,
    FOREIGN KEY (server_id) REFERENCES mcp_servers(id) ON DELETE CASCADE
);

-- 创建服务器环境变量表
CREATE TABLE IF NOT EXISTS mcp_server_env (
    server_id VARCHAR(255) NOT NULL,
    env_key VARCHAR(255) NOT NULL,
    env_value VARCHAR(1000),
    FOREIGN KEY (server_id) REFERENCES mcp_servers(id) ON DELETE CASCADE,
    PRIMARY KEY (server_id, env_key)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_mcp_servers_enabled ON mcp_servers(enabled);
CREATE INDEX IF NOT EXISTS idx_mcp_servers_transport ON mcp_servers(transport);
CREATE INDEX IF NOT EXISTS idx_mcp_server_args_server_id ON mcp_server_args(server_id);
CREATE INDEX IF NOT EXISTS idx_mcp_server_env_server_id ON mcp_server_env(server_id);