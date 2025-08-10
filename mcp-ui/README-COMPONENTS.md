# MCP 前端组件使用指南

本文档介绍了 MCP 管理平台的前端组件实现和使用方法。

## 项目结构

```
mcp-ui/src/
├── components/
│   ├── chat/                    # 聊天相关组件
│   │   ├── ChatInterface.tsx    # 主聊天界面
│   │   ├── MessageList.tsx      # 消息列表
│   │   ├── MessageInput.tsx     # 消息输入
│   │   ├── MessageItem.tsx      # 单条消息
│   │   ├── ToolResultDisplay.tsx # 工具结果显示
│   │   └── ChatInterface.css    # 聊天界面样式
│   └── server/                  # 服务器管理组件
│       ├── ServerManagement.tsx # 主管理界面
│       ├── ServerList.tsx       # 服务器列表
│       ├── ServerForm.tsx       # 服务器表单
│       ├── ServerStats.tsx      # 统计信息
│       └── ServerManagement.css # 管理界面样式
├── services/
│   └── serverApi.ts            # 服务器 API 服务
├── types/
│   ├── chat.ts                 # 聊天相关类型
│   └── server.ts               # 服务器相关类型
└── App.tsx                     # 主应用组件
```

## 主要功能

### 1. 统一聊天界面

**功能特性：**
- 实时消息发送和接收
- 支持多轮对话和会话管理
- 工具调用结果的可视化显示
- 消息历史记录
- 响应式设计

**使用方法：**
```tsx
import ChatInterface from './components/chat/ChatInterface';

<ChatInterface 
  conversationId={currentConversationId}
  onConversationChange={handleConversationChange}
/>
```

**API 集成：**
- `POST /api/chat/message` - 发送聊天消息
- `GET /api/chat/conversations/{id}` - 获取对话历史
- `GET /api/chat/tools` - 获取可用工具列表

### 2. MCP 服务器管理界面

**功能特性：**
- 服务器配置的增删改查
- 实时健康状态监控
- 连接测试和重连功能
- 统计信息展示
- 支持多种传输协议（STDIO、SSE、HTTP流）

**使用方法：**
```tsx
import ServerManagement from './components/server/ServerManagement';

<ServerManagement onError={handleError} />
```

**API 集成：**
- `GET /api/mcp/servers` - 获取服务器列表
- `POST /api/mcp/servers` - 添加/更新服务器
- `DELETE /api/mcp/servers/{id}` - 删除服务器
- `GET /api/mcp/servers/health` - 获取健康状态
- `POST /api/mcp/servers/{id}/reconnect` - 重新连接
- `POST /api/mcp/servers/{id}/test` - 测试连接

## 组件详细说明

### ChatInterface 组件

**Props：**
- `conversationId?: string` - 当前对话ID
- `onConversationChange?: (id: string) => void` - 对话变更回调

**状态管理：**
- 消息列表状态
- 加载状态
- 错误状态
- 对话ID管理

### ServerManagement 组件

**Props：**
- `onError?: (error: string) => void` - 错误处理回调

**主要功能：**
- 服务器列表展示
- 添加/编辑服务器表单
- 健康状态监控
- 统计信息展示

### ServerForm 组件

**支持的配置项：**
- 服务器ID和名称
- 传输协议选择
- URL配置（HTTP传输）
- 命令和参数（STDIO传输）
- 环境变量设置
- 启用/禁用状态

## 样式系统

### 设计原则
- 响应式设计，支持移动端
- 一致的颜色方案和间距
- 清晰的视觉层次
- 良好的可访问性

### 主要颜色
- 主色：`#007bff`
- 成功：`#28a745`
- 警告：`#ffc107`
- 错误：`#dc3545`
- 中性：`#6c757d`

### 响应式断点
- 移动端：`max-width: 768px`
- 平板端：`768px - 1024px`
- 桌面端：`min-width: 1024px`

## 开发指南

### 启动开发服务器
```bash
cd mcp-ui
npm install
npm run dev
```

### 构建生产版本
```bash
npm run build
```

### 代码规范
- 使用 TypeScript 进行类型检查
- 遵循 React Hooks 最佳实践
- 组件采用函数式组件
- 使用 CSS 模块化

### 错误处理
- 统一的错误提示机制
- 网络请求失败重试
- 用户友好的错误信息
- 错误边界保护

## API 配置

### 开发环境
前端开发服务器会自动代理 API 请求到后端服务：
- MCP Host 服务：`http://localhost:8087`
- MCP Client 服务：`http://localhost:8086`

### 生产环境
需要配置 Nginx 或其他反向代理来处理 API 路由。

## 部署说明

### 构建步骤
1. 安装依赖：`npm install`
2. 构建项目：`npm run build`
3. 部署 `dist` 目录到 Web 服务器

### 环境变量
可以通过环境变量配置 API 基础URL：
- `VITE_API_BASE_URL` - API 基础地址

## 故障排除

### 常见问题
1. **API 请求失败**
   - 检查后端服务是否启动
   - 验证 API 端点是否正确
   - 查看浏览器网络面板

2. **样式显示异常**
   - 清除浏览器缓存
   - 检查 CSS 文件是否正确加载

3. **组件渲染错误**
   - 查看浏览器控制台错误信息
   - 检查 TypeScript 类型错误

### 调试技巧
- 使用 React Developer Tools
- 启用详细的控制台日志
- 使用网络面板监控 API 请求
