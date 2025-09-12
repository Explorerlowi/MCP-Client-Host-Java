import React, { useState, useEffect } from 'react';
import type { McpServerSpec, MCPServerHealth, ServerConnectionStats } from '../../types/server';
import { ServerApiService } from '../../services/serverApi';
import ServerList from './ServerList';
import ServerForm from './ServerForm';
import JsonImportForm from './JsonImportForm';
import ServerStats from './ServerStats';
import './ServerManagement.css';

interface ServerManagementProps {
  onError?: (error: string) => void;
}

const ServerManagement: React.FC<ServerManagementProps> = ({ onError }) => {
  const [servers, setServers] = useState<McpServerSpec[]>([]);
  const [healthStatus, setHealthStatus] = useState<MCPServerHealth[]>([]);
  const [stats, setStats] = useState<ServerConnectionStats | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [showJsonImport, setShowJsonImport] = useState(false);
  const [editingServer, setEditingServer] = useState<McpServerSpec | null>(null);
  const [refreshInterval, setRefreshInterval] = useState<ReturnType<typeof setInterval> | null>(null);

  // 加载服务器列表
  const loadServers = async () => {
    try {
      setIsLoading(true);
      const [serversData, healthData, statsData] = await Promise.all([
        ServerApiService.getAllServers(),
        ServerApiService.getAllServerHealth(),
        ServerApiService.getConnectionStats()
      ]);
      
      setServers(serversData);
      setHealthStatus(healthData);
      setStats(statsData);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '加载服务器数据失败';
      onError?.(errorMessage);
      console.error('加载服务器数据失败:', error);
      
      // 设置默认的统计数据以避免渲染错误
      setStats({
        totalServers: 0,
        connectedServers: 0,
        disconnectedServers: 0,
        averageResponseTime: 0,
        lastUpdateTime: new Date()
      });
    } finally {
      setIsLoading(false);
    }
  };

  // 组件挂载时加载数据
  useEffect(() => {
    loadServers();
    
    // 设置定时刷新健康状态
    const interval = setInterval(async () => {
      try {
        const [healthData, statsData] = await Promise.all([
          ServerApiService.getAllServerHealth(),
          ServerApiService.getConnectionStats()
        ]);
        setHealthStatus(healthData);
        setStats(statsData);
      } catch (error) {
        console.error('刷新健康状态失败:', error);
        // 刷新失败时保持当前stats不变，避免设置为undefined
      }
    }, 30000); // 每30秒刷新一次

    setRefreshInterval(interval);

    return () => {
      if (refreshInterval) {
        clearInterval(refreshInterval);
      }
    };
  }, []);

  // 处理添加服务器
  const handleAddServer = () => {
    setEditingServer(null);
    setShowForm(true);
  };

  // 处理JSON导入
  const handleJsonImport = () => {
    setShowJsonImport(true);
  };



  // 处理编辑服务器
  const handleEditServer = (server: McpServerSpec) => {
    setEditingServer(server);
    setShowForm(true);
  };

  // 处理保存服务器
  const handleSaveServer = async (serverData: McpServerSpec) => {
    try {
      setIsLoading(true);
      await ServerApiService.addOrUpdateServer(serverData);
      setShowForm(false);
      setEditingServer(null);
      await loadServers(); // 重新加载数据
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '保存服务器配置失败';
      onError?.(errorMessage);
      console.error('保存服务器配置失败:', error);
    } finally {
      setIsLoading(false);
    }
  };

  // 处理删除服务器
  const handleDeleteServer = async (serverId: string) => {
    if (!confirm('确定要删除这个服务器配置吗？')) {
      return;
    }

    try {
      setIsLoading(true);
      await ServerApiService.deleteServer(serverId);
      await loadServers(); // 重新加载数据
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '删除服务器配置失败';
      onError?.(errorMessage);
      console.error('删除服务器配置失败:', error);
    } finally {
      setIsLoading(false);
    }
  };

  // 处理重连服务器
  const handleReconnectServer = async (serverId: string) => {
    try {
      setIsLoading(true);
      await ServerApiService.reconnectServer(serverId);
      // 等待一下再刷新状态
      setTimeout(async () => {
        try {
          const healthData = await ServerApiService.getAllServerHealth();
          setHealthStatus(healthData);
        } catch (error) {
          console.error('刷新健康状态失败:', error);
        }
      }, 2000);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '重新连接服务器失败';
      onError?.(errorMessage);
      console.error('重新连接服务器失败:', error);
    } finally {
      setIsLoading(false);
    }
  };

  // 已移除未使用的测试连接方法，避免未使用变量告警

  // 处理关闭服务器
  const handleShutdownServer = async (serverId: string) => {
    if (!confirm('确定要关闭这个服务器的连接吗？')) {
      return;
    }

    try {
      setIsLoading(true);
      await ServerApiService.shutdownServer(serverId);

      // 关闭成功后刷新健康状态
      setTimeout(async () => {
        try {
          const healthData = await ServerApiService.getAllServerHealth();
          setHealthStatus(healthData);
        } catch (error) {
          console.error('刷新健康状态失败:', error);
        }
      }, 1000);

      if (onError) {
        onError(`服务器连接已关闭: ${serverId}`);
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '关闭服务器连接失败';
      onError?.(errorMessage);
      console.error('关闭服务器连接失败:', error);
    } finally {
      setIsLoading(false);
    }
  };

  // 表单关闭后重置编辑状态
  useEffect(() => {
    if (!showForm) {
      setEditingServer(null);
    }
  }, [showForm]);

  // 处理JSON配置导入
  const handleImportFromJson = async (jsonConfig: string) => {
    try {
      setIsLoading(true);
      const result = await ServerApiService.importFromJson(jsonConfig);
      console.log('JSON导入结果:', result);

      // 导入成功后刷新服务器列表
      await loadServers();
      setShowJsonImport(false);

      if (onError) {
        onError(`配置导入成功: ${result.message}`);
      }
    } catch (error) {
      console.error('JSON导入失败:', error);
      if (onError) {
        onError(`配置导入失败: ${error instanceof Error ? error.message : '未知错误'}`);
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="h-full max-w-7xl mx-auto">
      {/* 机甲风格服务器管理容器 */}
      <div className="h-full bg-black/40 backdrop-blur-xl border border-cyan-400/30 rounded-2xl shadow-[0_0_40px_rgba(0,255,170,0.15)] relative overflow-hidden">
        {/* 顶部装饰条 */}
        <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-orange-400 via-red-400 to-orange-400 shadow-[0_0_10px_rgba(255,107,53,0.6)]"></div>
        
        {/* 管理头部 - 机甲控制台风格 */}
        <div className="flex items-center justify-between p-6 border-b border-orange-400/20 bg-gradient-to-r from-slate-950/50 to-slate-900/50">
          <div className="flex items-center space-x-4">
            <div className="relative">
              <div className="w-10 h-10 bg-gradient-to-r from-orange-400 to-red-400 rounded-full shadow-[0_0_20px_rgba(255,107,53,0.6)] animate-pulse"></div>
              <div className="absolute -top-1 -right-1 w-4 h-4 bg-red-400 rounded-full animate-ping"></div>
            </div>
            <div>
              <h2 className="font-['Orbitron'] text-xl font-bold bg-gradient-to-r from-orange-300 to-red-300 bg-clip-text text-transparent">
                MCP 服务器管理
              </h2>
              <div className="flex items-center space-x-2 text-xs text-orange-400/60">
                <div className="w-2 h-2 bg-red-400 rounded-full animate-pulse"></div>
                <span>服务器集群监控</span>
              </div>
            </div>
          </div>
          
          <div className="flex items-center space-x-3">
            <button
              onClick={loadServers}
              disabled={isLoading}
              className="bg-cyan-500/20 hover:bg-cyan-500/30 border border-cyan-500/50 text-cyan-300 shadow-[0_0_10px_rgba(0,255,170,0.3)] px-4 py-2 rounded-lg transition-colors flex items-center space-x-2"
              title="刷新数据"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
              </svg>
              <span>刷新</span>
            </button>
            <button
              onClick={handleJsonImport}
              disabled={isLoading}
              className="bg-emerald-500/20 hover:bg-emerald-500/30 border border-emerald-500/50 text-emerald-300 shadow-[0_0_10px_rgba(0,255,170,0.3)] px-4 py-2 rounded-lg transition-colors flex items-center space-x-2"
              title="从JSON配置导入服务器"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
              </svg>
              <span>JSON导入</span>
            </button>
            <button
              onClick={handleAddServer}
              disabled={isLoading}
              className="bg-orange-500/20 hover:bg-orange-500/30 border border-orange-500/50 text-orange-300 shadow-[0_0_10px_rgba(255,107,53,0.3)] px-4 py-2 rounded-lg transition-colors flex items-center space-x-2"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"/>
              </svg>
              <span>添加服务器</span>
            </button>
          </div>
        </div>

        {/* 主内容区域 - 全息显示效果 */}
        <div className="flex-1 relative overflow-hidden">
          <div className="h-full p-6 overflow-y-auto">
            <div className="space-y-6">
              {/* 统计信息 */}
              {stats && <ServerStats stats={stats} />}

              {/* 服务器列表 */}
              <ServerList
                servers={servers}
                healthStatus={healthStatus}
                onEdit={handleEditServer}
                onDelete={handleDeleteServer}
                onReconnect={handleReconnectServer}
                onShutdown={handleShutdownServer}
                isLoading={isLoading}
              />
            </div>
          </div>
          
          {/* 侧边全息装饰 */}
          <div className="absolute top-0 right-0 w-1 h-full bg-gradient-to-b from-orange-400/30 to-red-400/30 shadow-[0_0_10px_rgba(255,107,53,0.3)]"></div>
        </div>

        {/* 服务器表单 */}
        <ServerForm
          open={showForm}
          onOpenChange={setShowForm}
          server={editingServer}
          onSave={handleSaveServer}
          isLoading={isLoading}
        />

        {/* JSON导入表单 */}
        <JsonImportForm
          open={showJsonImport}
          onOpenChange={setShowJsonImport}
          onImport={handleImportFromJson}
          isLoading={isLoading}
        />

        {/* 加载覆盖层 - 机甲风格 */}
        {isLoading && (
          <div className="absolute inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center z-50">
            <div className="bg-slate-950/90 border border-cyan-400/30 rounded-xl p-6 shadow-[0_0_30px_rgba(0,255,170,0.2)] flex items-center space-x-4">
              <div className="w-8 h-8 border-2 border-cyan-400/30 border-t-cyan-400 rounded-full animate-spin"></div>
              <span className="text-cyan-300 font-['Orbitron']">系统处理中...</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ServerManagement;
