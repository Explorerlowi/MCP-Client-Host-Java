import React, { useState } from 'react';
import type { McpServerSpec, MCPServerHealth, MCPTool, MCPResource, MCPPrompt } from '../../types/server';
import { TransportType } from '../../types/server';
import { format } from 'date-fns';
import { ServerApiService } from '../../services/serverApi';
import ServerDetailsModal from './ServerDetailsModal';
import './ServerList.css';

interface ServerListProps {
  servers: McpServerSpec[];
  healthStatus: MCPServerHealth[];
  onEdit: (server: McpServerSpec) => void;
  onDelete: (serverId: string) => void;
  onReconnect: (serverId: string) => void;
  // onTest: (serverId: string) => void; // 未使用，去除避免告警
  onShutdown: (serverId: string) => void;
  isLoading: boolean;
}

const ServerList: React.FC<ServerListProps> = ({
  servers,
  healthStatus,
  onEdit,
  onDelete,
  onReconnect,
  onShutdown,
  isLoading
}) => {
  const [expandedServerId, setExpandedServerId] = useState<string | null>(null);
  const [expandedType, setExpandedType] = useState<'tools' | 'resources' | 'prompts' | null>(null);
  const [serverTools, setServerTools] = useState<Record<string, MCPTool[]>>({});
  const [serverResources, setServerResources] = useState<Record<string, MCPResource[]>>({});
  const [serverPrompts, setServerPrompts] = useState<Record<string, MCPPrompt[]>>({});
  const [loadingTools, setLoadingTools] = useState<string | null>(null);
  const [loadingResources, setLoadingResources] = useState<string | null>(null);
  const [loadingPrompts, setLoadingPrompts] = useState<string | null>(null);
  // 获取服务器健康状态
  const getServerHealth = (serverId: string): MCPServerHealth | undefined => {
    return healthStatus.find(health => health.serverId === serverId);
  };

  // 获取状态显示样式
  const getStatusClass = (health: MCPServerHealth | undefined): string => {
    if (!health) return 'status-unknown';
    if (health.connected) return 'status-connected';
    return 'status-disconnected';
  };

  // 获取状态文本
  const getStatusText = (health: MCPServerHealth | undefined): string => {
    if (!health) return '未知';
    if (health.connected) return '已连接';
    return '已断开';
  };

  // 获取传输类型显示文本
  const getTransportText = (transport: TransportType): string => {
    switch (transport) {
      case TransportType.STDIO:
        return 'STDIO';
      case TransportType.SSE:
        return 'SSE';
      case TransportType.STREAMABLEHTTP:
        return 'HTTP流';
      default:
        return transport;
    }
  };

  // 格式化日期
  const formatDate = (date: Date | undefined): string => {
    if (!date) return '-';
    return format(new Date(date), 'yyyy-MM-dd HH:mm:ss');
  };

  // 处理内容展开/收起
  const handleToggleContent = async (serverId: string, type: 'tools' | 'resources' | 'prompts') => {
    if (expandedServerId === serverId && expandedType === type) {
      // 收起
      setExpandedServerId(null);
      setExpandedType(null);
    } else {
      // 展开
      setExpandedServerId(serverId);
      setExpandedType(type);
      
      // 根据类型加载对应的数据
      if (type === 'tools' && !serverTools[serverId]) {
        setLoadingTools(serverId);
        try {
          const tools = await ServerApiService.getServerTools(serverId);
          setServerTools(prev => ({ ...prev, [serverId]: tools }));
        } catch (error) {
          console.error('获取工具列表失败:', error);
          setServerTools(prev => ({ ...prev, [serverId]: [] }));
        } finally {
          setLoadingTools(null);
        }
      } else if (type === 'resources' && !serverResources[serverId]) {
        setLoadingResources(serverId);
        try {
          const resources = await ServerApiService.getServerResources(serverId);
          setServerResources(prev => ({ ...prev, [serverId]: resources }));
        } catch (error) {
          console.error('获取资源列表失败:', error);
          setServerResources(prev => ({ ...prev, [serverId]: [] }));
        } finally {
          setLoadingResources(null);
        }
      } else if (type === 'prompts' && !serverPrompts[serverId]) {
        setLoadingPrompts(serverId);
        try {
          const prompts = await ServerApiService.getServerPrompts(serverId);
          setServerPrompts(prev => ({ ...prev, [serverId]: prompts }));
        } catch (error) {
          console.error('获取提示模板列表失败:', error);
          setServerPrompts(prev => ({ ...prev, [serverId]: [] }));
        } finally {
          setLoadingPrompts(null);
        }
      }
    }
  };

  // 格式化JSON Schema
  const formatSchema = (schema: string | undefined): string => {
    if (!schema) return '{}';
    try {
      return JSON.stringify(JSON.parse(schema), null, 2);
    } catch {
      return schema;
    }
  };

  if (servers.length === 0) {
    return (
      <div className="bg-black/30 backdrop-blur-xl border border-orange-400/30 rounded-xl shadow-[0_0_30px_rgba(255,107,53,0.1)] relative overflow-hidden">
        {/* 顶部装饰条 */}
        <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-orange-400 via-red-400 to-orange-400 shadow-[0_0_8px_rgba(255,107,53,0.4)]"></div>
        
        {/* 空状态内容 */}
        <div className="flex flex-col items-center justify-center p-12 text-center">
          {/* 机甲风格图标 */}
          <div className="relative mb-8">
            <div className="w-20 h-20 bg-gradient-to-br from-orange-400/20 to-red-400/20 rounded-2xl backdrop-blur-sm border border-orange-400/30 flex items-center justify-center shadow-[0_0_40px_rgba(255,107,53,0.3)]">
              <div className="relative">
                <div className="w-12 h-12 bg-gradient-to-r from-orange-400 to-red-400 rounded-xl shadow-[0_0_20px_rgba(255,107,53,0.6)] flex items-center justify-center">
                  <svg className="w-8 h-8 text-black" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01"/>
                  </svg>
                </div>
                {/* 脉冲环 */}
                <div className="absolute -inset-2 rounded-xl border-2 border-orange-400/50 animate-ping"></div>
              </div>
            </div>
          </div>
          
          <h3 className="font-['Orbitron'] text-xl font-bold bg-gradient-to-r from-orange-300 to-red-300 bg-clip-text text-transparent mb-4">
            暂无服务器配置
          </h3>
          <p className="text-orange-200/70 text-base mb-6 max-w-md">
            点击"添加服务器"按钮来配置您的第一个 MCP 服务器，开始构建您的AI助手网络
          </p>
          
          {/* 提示卡片 */}
          <div className="bg-slate-950/30 border border-orange-400/20 rounded-lg p-4 max-w-sm">
            <div className="flex items-center space-x-3">
              <div className="w-2 h-2 bg-orange-400 rounded-full animate-pulse"></div>
              <span className="text-orange-300/80 text-sm">建议先导入JSON配置或手动添加服务器</span>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-black/30 backdrop-blur-xl border border-orange-400/30 rounded-xl shadow-[0_0_30px_rgba(255,107,53,0.1)] relative overflow-hidden">
      {/* 顶部装饰条 */}
      <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-orange-400 via-red-400 to-orange-400 shadow-[0_0_8px_rgba(255,107,53,0.4)]"></div>
      
      {/* 列表头部 */}
      <div className="flex items-center justify-between p-6 border-b border-orange-400/20">
        <div className="flex items-center space-x-3">
          <div className="w-8 h-8 bg-gradient-to-r from-orange-400 to-red-400 rounded-lg shadow-[0_0_15px_rgba(255,107,53,0.5)] flex items-center justify-center">
            <svg className="w-5 h-5 text-black" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 6h16M4 10h16M4 14h16M4 18h16"/>
            </svg>
          </div>
          <h3 className="font-['Orbitron'] text-lg font-bold bg-gradient-to-r from-orange-300 to-red-300 bg-clip-text text-transparent">
            服务器列表 ({servers.length})
          </h3>
        </div>
      </div>
      
      {/* 表格容器 */}
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="bg-slate-950/50 border-b border-orange-400/20">
              <th className="px-6 py-4 text-left text-xs font-semibold text-orange-300 uppercase tracking-wider">服务器ID</th>
              <th className="px-6 py-4 text-left text-xs font-semibold text-orange-300 uppercase tracking-wider">名称</th>
              <th className="px-6 py-4 text-left text-xs font-semibold text-orange-300 uppercase tracking-wider">传输协议</th>
              <th className="px-6 py-4 text-left text-xs font-semibold text-orange-300 uppercase tracking-wider">状态</th>
              <th className="px-6 py-4 text-left text-xs font-semibold text-orange-300 uppercase tracking-wider">描述</th>
              <th className="px-6 py-4 text-left text-xs font-semibold text-orange-300 uppercase tracking-wider">最后检查</th>
              <th className="px-6 py-4 text-left text-xs font-semibold text-orange-300 uppercase tracking-wider">操作</th>
            </tr>
          </thead>
          <tbody>
            {servers.map((server) => {
              const health = getServerHealth(server.id);
              return (
                <React.Fragment key={server.id}>
                  <tr className={`border-b border-orange-400/10 hover:bg-slate-950/30 transition-colors ${!server.enabled ? 'opacity-60' : ''}`}>
                  <td className="px-6 py-4">
                    <div className="flex items-center space-x-2">
                      <span className="font-mono text-sm text-cyan-300">{server.id}</span>
                      {!server.enabled && (
                        <span className="px-2 py-1 text-xs bg-gray-600/50 text-gray-300 rounded border border-gray-500/50">
                          已禁用
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <span className="text-gray-200 font-medium">{server.name || server.id}</span>
                  </td>
                  <td className="px-6 py-4">
                    <span className={`px-3 py-1 text-xs font-medium rounded-full border ${
                      server.transport.toLowerCase() === 'stdio' ? 'bg-blue-500/20 text-blue-300 border-blue-500/50' :
                      server.transport.toLowerCase() === 'sse' ? 'bg-purple-500/20 text-purple-300 border-purple-500/50' :
                      'bg-green-500/20 text-green-300 border-green-500/50'
                    }`}>
                      {getTransportText(server.transport)}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center space-x-2">
                      <div className={`w-2 h-2 rounded-full ${
                        !health ? 'bg-gray-500' :
                        health.connected ? 'bg-emerald-400 shadow-[0_0_6px_rgba(34,197,94,0.6)]' :
                        'bg-red-400 shadow-[0_0_6px_rgba(239,68,68,0.6)]'
                      } animate-pulse`}></div>
                      <span className={`text-sm font-medium ${
                        !health ? 'text-gray-400' :
                        health.connected ? 'text-emerald-300' : 'text-red-300'
                      }`}>
                        {getStatusText(health)}
                      </span>
                      {health?.responseTime && (
                        <span className="text-xs text-gray-500">({health.responseTime}ms)</span>
                      )}
                    </div>
                    {health?.errorMessage && (
                      <div className="mt-1 text-xs text-red-400 truncate max-w-xs" title={health.errorMessage}>
                        ⚠️ {health.errorMessage.substring(0, 50)}...
                      </div>
                    )}
                  </td>
                  <td className="px-6 py-4">
                    <div className="text-sm text-gray-400 max-w-xs truncate" title={server.description}>
                      {server.description || '-'}
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <div className="text-xs text-gray-500 font-mono">
                      {formatDate(health?.lastCheck)}
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center space-x-2">
                      <button
                        onClick={() => onEdit(server)}
                        disabled={isLoading}
                        className="p-2 bg-cyan-500/20 hover:bg-cyan-500/30 border border-cyan-500/50 text-cyan-300 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        title="编辑配置"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/>
                        </svg>
                      </button>
                      <button
                        onClick={() => handleToggleContent(server.id, 'tools')}
                        disabled={isLoading}
                        className={`p-2 border rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed ${
                          expandedServerId === server.id && expandedType === 'tools' 
                            ? 'bg-orange-500/30 border-orange-500/50 text-orange-300' 
                            : 'bg-gray-600/20 hover:bg-gray-600/30 border-gray-600/50 text-gray-300'
                        }`}
                        title={expandedServerId === server.id && expandedType === 'tools' ? '收起工具列表' : '展开工具列表'}
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/>
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
                        </svg>
                      </button>
                      <button
                        onClick={() => handleToggleContent(server.id, 'resources')}
                        disabled={isLoading}
                        className={`p-2 border rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed ${
                          expandedServerId === server.id && expandedType === 'resources' 
                            ? 'bg-purple-500/30 border-purple-500/50 text-purple-300' 
                            : 'bg-gray-600/20 hover:bg-gray-600/30 border-gray-600/50 text-gray-300'
                        }`}
                        title={expandedServerId === server.id && expandedType === 'resources' ? '收起资源列表' : '展开资源列表'}
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2H5a2 2 0 00-2-2V7zm0 0a2 2 0 012-2h6l2 2h6a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2V7z"/>
                        </svg>
                      </button>
                      <button
                        onClick={() => handleToggleContent(server.id, 'prompts')}
                        disabled={isLoading}
                        className={`p-2 border rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed ${
                          expandedServerId === server.id && expandedType === 'prompts' 
                            ? 'bg-blue-500/30 border-blue-500/50 text-blue-300' 
                            : 'bg-gray-600/20 hover:bg-gray-600/30 border-gray-600/50 text-gray-300'
                        }`}
                        title={expandedServerId === server.id && expandedType === 'prompts' ? '收起提示模板列表' : '展开提示模板列表'}
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"/>
                        </svg>
                      </button>
                      <button
                        onClick={() => onReconnect(server.id)}
                        disabled={isLoading}
                        className="p-2 bg-yellow-500/20 hover:bg-yellow-500/30 border border-yellow-500/50 text-yellow-300 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        title="重新连接"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
                        </svg>
                      </button>
                      <button
                        onClick={() => onShutdown(server.id)}
                        disabled={isLoading}
                        className="p-2 bg-orange-500/20 hover:bg-orange-500/30 border border-orange-500/50 text-orange-300 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        title="关闭服务器"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728L5.636 5.636m12.728 12.728L18.364 5.636M5.636 18.364l12.728-12.728"/>
                        </svg>
                      </button>
                      <button
                        onClick={() => onDelete(server.id)}
                        disabled={isLoading}
                        className="p-2 bg-red-500/20 hover:bg-red-500/30 border border-red-500/50 text-red-300 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        title="删除配置"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                        </svg>
                      </button>
                    </div>
                  </td>
                  </tr>
                </React.Fragment>
              );
            })}
          </tbody>
        </table>
      </div>
      {/* 服务器详情模态框 */}
      <ServerDetailsModal
        isOpen={!!expandedServerId}
        onClose={() => setExpandedServerId(null)}
        server={expandedServerId ? servers.find(s => s.id === expandedServerId) || null : null}
        type={expandedType || 'tools'}
        tools={expandedServerId ? serverTools[expandedServerId] || [] : []}
        resources={expandedServerId ? serverResources[expandedServerId] || [] : []}
        prompts={expandedServerId ? serverPrompts[expandedServerId] || [] : []}
        isLoading={
          expandedType === 'tools' ? loadingTools === expandedServerId :
          expandedType === 'resources' ? loadingResources === expandedServerId :
          expandedType === 'prompts' ? loadingPrompts === expandedServerId :
          false
        }
      />
    </div>
  );
};

export default ServerList;
