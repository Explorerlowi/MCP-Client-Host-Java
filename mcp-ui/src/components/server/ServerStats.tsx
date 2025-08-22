import React from 'react';
import type { ServerConnectionStats } from '../../types/server';

interface ServerStatsProps {
  stats: ServerConnectionStats;
}

const ServerStats: React.FC<ServerStatsProps> = ({ stats }) => {
  // 计算连接率
  const connectionRate = stats.totalServers > 0 
    ? Math.round((stats.connectedServers / stats.totalServers) * 100) 
    : 0;





  // 格式化更新时间
  const formatUpdateTime = (date: Date | string | number | null | undefined): string => {
    if (!date) {
      return '未知';
    }
    
    try {
      let dateObj: Date;
      
      if (date instanceof Date) {
        dateObj = date;
      } else if (typeof date === 'string' || typeof date === 'number') {
        dateObj = new Date(date);
      } else {
        return '无效时间';
      }
      
      if (isNaN(dateObj.getTime())) {
        return '格式错误';
      }
      
      return dateObj.toLocaleString('zh-CN');
    } catch (error) {
      console.error('时间格式化错误:', error);
      return '格式错误';
    }
  };

  const getLastUpdateTime = (): string => {
    if (stats.lastUpdateTime) {
      return formatUpdateTime(stats.lastUpdateTime);
    } else if (stats.healthCheckTime) {
      // 将健康检查时间戳转换为日期
      return formatUpdateTime(new Date(stats.healthCheckTime * 1000));
    }
    return '未知';
  };

  const getAverageResponseTime = (): string => {
    if (stats.averageResponseTime !== undefined) {
      return `${stats.averageResponseTime.toFixed(1)}ms`;
    }
    return 'NaN';
  };

  return (
    <div className="bg-black/30 backdrop-blur-xl border border-orange-400/30 rounded-xl shadow-[0_0_30px_rgba(255,107,53,0.1)] relative overflow-hidden">
      {/* 顶部装饰条 */}
      <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-orange-400 via-red-400 to-orange-400 shadow-[0_0_8px_rgba(255,107,53,0.4)]"></div>
      
      {/* 统计头部 */}
      <div className="flex items-center justify-between p-6 border-b border-orange-400/20">
        <div className="flex items-center space-x-3">
          <div className="w-8 h-8 bg-gradient-to-r from-orange-400 to-red-400 rounded-lg shadow-[0_0_15px_rgba(255,107,53,0.5)] flex items-center justify-center">
            <svg className="w-5 h-5 text-black" fill="currentColor" viewBox="0 0 24 24">
              <path d="M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z"/>
            </svg>
          </div>
          <h3 className="font-['Orbitron'] text-lg font-bold bg-gradient-to-r from-orange-300 to-red-300 bg-clip-text text-transparent">
            服务器统计
          </h3>
        </div>
        <div className="text-xs text-orange-400/60">
          最后更新: {getLastUpdateTime()}
        </div>
      </div>
      
      {/* 统计卡片网格 */}
      <div className="p-6">
        <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 gap-4 mb-6">
          {/* 总服务器数 */}
          <div className="bg-slate-950/50 border border-cyan-400/30 rounded-lg p-4 backdrop-blur-sm shadow-[0_0_15px_rgba(0,255,170,0.1)] relative group hover:border-cyan-400/50 transition-colors">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 bg-gradient-to-r from-cyan-400/20 to-cyan-500/20 rounded-lg flex items-center justify-center">
                <svg className="w-6 h-6 text-cyan-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01"/>
                </svg>
              </div>
              <div>
                <div className="text-2xl font-bold text-cyan-300">{stats.totalServers}</div>
                <div className="text-xs text-cyan-400/60">总服务器数</div>
              </div>
            </div>
          </div>

          {/* 已连接服务器 */}
          <div className="bg-slate-950/50 border border-emerald-400/30 rounded-lg p-4 backdrop-blur-sm shadow-[0_0_15px_rgba(0,255,170,0.1)] relative group hover:border-emerald-400/50 transition-colors">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 bg-gradient-to-r from-emerald-400/20 to-emerald-500/20 rounded-lg flex items-center justify-center">
                <svg className="w-6 h-6 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
                </svg>
              </div>
              <div>
                <div className="text-2xl font-bold text-emerald-300">{stats.connectedServers}</div>
                <div className="text-xs text-emerald-400/60">已连接</div>
              </div>
            </div>
          </div>

          {/* 已断开服务器 */}
          <div className="bg-slate-950/50 border border-red-400/30 rounded-lg p-4 backdrop-blur-sm shadow-[0_0_15px_rgba(239,68,68,0.1)] relative group hover:border-red-400/50 transition-colors">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 bg-gradient-to-r from-red-400/20 to-red-500/20 rounded-lg flex items-center justify-center">
                <svg className="w-6 h-6 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"/>
                </svg>
              </div>
              <div>
                <div className="text-2xl font-bold text-red-300">{stats.disconnectedServers}</div>
                <div className="text-xs text-red-400/60">已断开</div>
              </div>
            </div>
          </div>

          {/* 连接率 */}
          <div className={`bg-slate-950/50 border rounded-lg p-4 backdrop-blur-sm shadow-[0_0_15px_rgba(0,255,170,0.1)] relative group transition-colors ${
            connectionRate >= 80 ? 'border-emerald-400/30 hover:border-emerald-400/50' :
            connectionRate >= 60 ? 'border-cyan-400/30 hover:border-cyan-400/50' :
            connectionRate >= 40 ? 'border-yellow-400/30 hover:border-yellow-400/50' :
            'border-red-400/30 hover:border-red-400/50'
          }`}>
            <div className="flex items-center space-x-3 mb-2">
              <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                connectionRate >= 80 ? 'bg-gradient-to-r from-emerald-400/20 to-emerald-500/20' :
                connectionRate >= 60 ? 'bg-gradient-to-r from-cyan-400/20 to-cyan-500/20' :
                connectionRate >= 40 ? 'bg-gradient-to-r from-yellow-400/20 to-yellow-500/20' :
                'bg-gradient-to-r from-red-400/20 to-red-500/20'
              }`}>
                <svg className={`w-6 h-6 ${
                  connectionRate >= 80 ? 'text-emerald-400' :
                  connectionRate >= 60 ? 'text-cyan-400' :
                  connectionRate >= 40 ? 'text-yellow-400' :
                  'text-red-400'
                }`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"/>
                </svg>
              </div>
              <div>
                <div className={`text-2xl font-bold ${
                  connectionRate >= 80 ? 'text-emerald-300' :
                  connectionRate >= 60 ? 'text-cyan-300' :
                  connectionRate >= 40 ? 'text-yellow-300' :
                  'text-red-300'
                }`}>{connectionRate}%</div>
                <div className="text-xs text-gray-400">连接率</div>
              </div>
            </div>
            {/* 进度条 */}
            <div className="w-full h-1 bg-gray-700 rounded-full overflow-hidden">
              <div 
                className={`h-full transition-all duration-500 ${
                  connectionRate >= 80 ? 'bg-gradient-to-r from-emerald-400 to-emerald-500' :
                  connectionRate >= 60 ? 'bg-gradient-to-r from-cyan-400 to-cyan-500' :
                  connectionRate >= 40 ? 'bg-gradient-to-r from-yellow-400 to-yellow-500' :
                  'bg-gradient-to-r from-red-400 to-red-500'
                } shadow-[0_0_8px_rgba(0,255,170,0.6)]`}
                style={{ width: `${connectionRate}%` }}
              ></div>
            </div>
          </div>

          {/* 平均响应时间 */}
          <div className="bg-slate-950/50 border border-violet-400/30 rounded-lg p-4 backdrop-blur-sm shadow-[0_0_15px_rgba(139,92,246,0.1)] relative group hover:border-violet-400/50 transition-colors">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 bg-gradient-to-r from-violet-400/20 to-violet-500/20 rounded-lg flex items-center justify-center">
                <svg className="w-6 h-6 text-violet-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 10V3L4 14h7v7l9-11h-7z"/>
                </svg>
              </div>
              <div>
                <div className="text-2xl font-bold text-violet-300">
                  {getAverageResponseTime()}
                </div>
                <div className="text-xs text-violet-400/60">平均响应时间</div>
              </div>
            </div>
          </div>
        </div>

        {/* 系统状态指示器 */}
        <div className="flex items-center justify-between p-4 bg-slate-950/30 border border-orange-400/20 rounded-lg backdrop-blur-sm">
          <div className="flex items-center space-x-3">
            <div className={`w-3 h-3 rounded-full animate-pulse ${
              connectionRate >= 80 ? 'bg-emerald-400 shadow-[0_0_10px_rgba(34,197,94,0.6)]' :
              connectionRate >= 60 ? 'bg-cyan-400 shadow-[0_0_10px_rgba(6,182,212,0.6)]' :
              connectionRate >= 40 ? 'bg-yellow-400 shadow-[0_0_10px_rgba(251,191,36,0.6)]' :
              'bg-red-400 shadow-[0_0_10px_rgba(239,68,68,0.6)]'
            }`}></div>
            <span className="text-gray-300 font-medium">
              {connectionRate >= 80 && '系统运行良好'}
              {connectionRate >= 60 && connectionRate < 80 && '系统运行正常'}
              {connectionRate >= 40 && connectionRate < 60 && '部分服务异常'}
              {connectionRate < 40 && '系统状态异常'}
            </span>
          </div>
          
          {stats.totalServers === 0 && (
            <div className="flex items-center space-x-2 text-yellow-400">
              <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                <path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/>
              </svg>
              <span className="text-sm">尚未配置任何服务器</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ServerStats;
