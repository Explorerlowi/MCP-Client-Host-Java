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

-- ----------------------------
-- 4、初始化默认服务器配置数据
-- ----------------------------

-- 插入 Bazi 服务器配置（如果不存在）
INSERT OR IGNORE INTO mcp_servers (id, name, description, type, command, timeout, disabled, created_at, updated_at)
VALUES ('Bazi', 'Bazi', '八字命理分析服务器', 'STDIO', 'npx', 60, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 插入 Bazi 服务器参数
INSERT OR IGNORE INTO mcp_server_args (server_id, arg)
VALUES ('Bazi', 'bazi-mcp');

-- 插入 bilibili 服务器配置（如果不存在）
INSERT OR IGNORE INTO mcp_servers (id, name, description, type, command, timeout, disabled, created_at, updated_at)
VALUES ('bilibili', 'bilibili', 'Bilibili API 服务器', 'STDIO', 'uvx', 60, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 插入 bilibili 服务器参数
INSERT OR IGNORE INTO mcp_server_args (server_id, arg)
VALUES
    ('bilibili', '--index-url'),
    ('bilibili', 'https://mirrors.aliyun.com/pypi/simple/'),
    ('bilibili', 'bilibili-api-mcp-server');

-- ----------------------------
-- 3、MCP 服务器环境变量表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mcp_server_env (
    server_id VARCHAR(255) NOT NULL,
    env_key VARCHAR(255) NOT NULL,
    env_value VARCHAR(1000),
    PRIMARY KEY (server_id, env_key)
);