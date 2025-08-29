-- ----------------------------
-- MCP Client 数据库初始化脚本
-- 只在表不存在时创建，保留现有数据
-- ----------------------------

-- ----------------------------
-- 1、MCP 服务器配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mcp_servers (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255),
    description TEXT,
    type VARCHAR(50) NOT NULL,
    url VARCHAR(500),
    command VARCHAR(500),
    timeout INTEGER DEFAULT 60,
    disabled INTEGER DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------
-- 2、MCP 服务器参数表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mcp_server_args (
    server_id VARCHAR(255) NOT NULL,
    arg VARCHAR(500) NOT NULL
);

-- ----------------------------
-- 3、MCP 服务器环境变量表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mcp_server_env (
    server_id VARCHAR(255) NOT NULL,
    env_key VARCHAR(255) NOT NULL,
    env_value VARCHAR(1000),
    PRIMARY KEY (server_id, env_key)
);