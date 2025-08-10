import React from 'react';
import { createPortal } from 'react-dom';
import type { MCPTool, MCPResource, MCPPrompt, McpServerSpec } from '../../types/server';

interface ServerDetailsModalProps {
  isOpen: boolean;
  onClose: () => void;
  server: McpServerSpec | null;
  type: 'tools' | 'resources' | 'prompts';
  tools?: MCPTool[];
  resources?: MCPResource[];
  prompts?: MCPPrompt[];
  isLoading: boolean;
}

const ServerDetailsModal: React.FC<ServerDetailsModalProps> = ({
  isOpen,
  onClose,
  server,
  type,
  tools = [],
  resources = [],
  prompts = [],
  isLoading
}) => {
  // 处理键盘事件
  React.useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    if (isOpen) {
      document.addEventListener('keydown', handleKeyDown);
      // 防止背景滚动
      document.body.style.overflow = 'hidden';
    }

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = 'unset';
    };
  }, [isOpen, onClose]);

  if (!isOpen || !server) return null;

  const formatSchema = (schema: string | undefined): string => {
    if (!schema) return '{}';
    try {
      return JSON.stringify(JSON.parse(schema), null, 2);
    } catch {
      return schema;
    }
  };

  const getModalConfig = () => {
    switch (type) {
      case 'tools':
        return {
          title: '工具列表',
          color: 'orange',
          icon: (
            <svg className="w-5 h-5 text-black" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/>
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
            </svg>
          ),
          data: tools,
          emptyMessage: '该服务器尚未提供任何工具'
        };
      case 'resources':
        return {
          title: '资源列表',
          color: 'purple',
          icon: (
            <svg className="w-5 h-5 text-black" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2H5a2 2 0 00-2-2V7zm0 0a2 2 0 012-2h6l2 2h6a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2V7z"/>
            </svg>
          ),
          data: resources,
          emptyMessage: '该服务器尚未提供任何资源'
        };
      case 'prompts':
        return {
          title: '提示模板列表',
          color: 'blue',
          icon: (
            <svg className="w-5 h-5 text-black" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"/>
            </svg>
          ),
          data: prompts,
          emptyMessage: '该服务器尚未提供任何提示模板'
        };
    }
  };

  const config = getModalConfig();
  const colorMap = {
    orange: {
      border: 'border-orange-400/30',
      shadow: 'shadow-[0_0_40px_rgba(255,107,53,0.2)]',
      gradient: 'from-orange-400 via-red-400 to-orange-400',
      gradientShadow: 'shadow-[0_0_8px_rgba(255,107,53,0.4)]',
      headerBorder: 'border-orange-400/20',
      bg: 'from-orange-400 to-red-400',
      bgShadow: 'shadow-[0_0_15px_rgba(255,107,53,0.5)]',
      text: 'from-orange-300 to-red-300',
      cardBorder: 'border-cyan-400/30',
      cardText: 'text-cyan-300'
    },
    purple: {
      border: 'border-purple-400/30',
      shadow: 'shadow-[0_0_40px_rgba(139,92,246,0.2)]',
      gradient: 'from-purple-400 via-violet-400 to-purple-400',
      gradientShadow: 'shadow-[0_0_8px_rgba(139,92,246,0.4)]',
      headerBorder: 'border-purple-400/20',
      bg: 'from-purple-400 to-violet-400',
      bgShadow: 'shadow-[0_0_15px_rgba(139,92,246,0.5)]',
      text: 'from-purple-300 to-violet-300',
      cardBorder: 'border-purple-400/30',
      cardText: 'text-purple-300'
    },
    blue: {
      border: 'border-blue-400/30',
      shadow: 'shadow-[0_0_40px_rgba(59,130,246,0.2)]',
      gradient: 'from-blue-400 via-cyan-400 to-blue-400',
      gradientShadow: 'shadow-[0_0_8px_rgba(59,130,246,0.4)]',
      headerBorder: 'border-blue-400/20',
      bg: 'from-blue-400 to-cyan-400',
      bgShadow: 'shadow-[0_0_15px_rgba(59,130,246,0.5)]',
      text: 'from-blue-300 to-cyan-300',
      cardBorder: 'border-blue-400/30',
      cardText: 'text-blue-300'
    }
  };

  const colors = colorMap[config.color as keyof typeof colorMap];

  // 点击遮罩关闭
  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  // 使用 Portal 确保模态框渲染在根元素外
  const modalContent = (
    <div 
      className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4" 
      style={{ zIndex: 9999 }}
      onClick={handleOverlayClick}
    >
      <div 
        className={`bg-black/90 backdrop-blur-xl border ${colors.border} rounded-2xl ${colors.shadow} w-full max-w-6xl max-h-[90vh] relative flex flex-col overflow-hidden`}
        onClick={(e) => e.stopPropagation()}
      >
        {/* 顶部装饰条 */}
        <div className={`absolute top-0 left-0 w-full h-1 bg-gradient-to-r ${colors.gradient} ${colors.gradientShadow}`}></div>
        
        {/* 模态框头部 */}
        <div className={`flex items-center justify-between p-6 border-b ${colors.headerBorder} flex-shrink-0`}>
          <div className="flex items-center space-x-3">
            <div className={`w-8 h-8 bg-gradient-to-r ${colors.bg} rounded-lg ${colors.bgShadow} flex items-center justify-center`}>
              {config.icon}
            </div>
            <div>
              <h3 className={`font-['Orbitron'] text-xl font-bold bg-gradient-to-r ${colors.text} bg-clip-text text-transparent`}>
                {config.title}
              </h3>
              <div className={`text-sm ${config.color === 'orange' ? 'text-orange-400/60' : config.color === 'purple' ? 'text-purple-400/60' : 'text-blue-400/60'}`}>
                服务器: {server.name || server.id}
              </div>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 bg-red-500/20 hover:bg-red-500/30 border border-red-500/50 text-red-300 rounded-lg transition-colors"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"/>
            </svg>
          </button>
        </div>
        
        {/* 模态框内容 - 可滚动区域 */}
        <div className="flex-1 overflow-y-auto min-h-0 relative" style={{ maxHeight: 'calc(90vh - 120px)' }}>
          <div className="p-6">
          {isLoading ? (
            <div className="flex items-center justify-center h-64">
              <div className={`bg-slate-950/90 border ${colors.cardBorder} rounded-xl p-6 ${colors.shadow} flex items-center space-x-4`}>
                <div className={`w-8 h-8 border-2 ${colors.cardBorder} rounded-full animate-spin ${
                  config.color === 'orange' ? 'border-t-orange-400' :
                  config.color === 'purple' ? 'border-t-purple-400' :
                  'border-t-blue-400'
                }`}></div>
                <span className={`${colors.cardText} font-['Orbitron']`}>正在获取{config.title}...</span>
              </div>
            </div>
          ) : config.data.length > 0 ? (
            <div className={`grid grid-cols-1 ${type === 'tools' || type === 'prompts' ? 'md:grid-cols-2 lg:grid-cols-3' : 'md:grid-cols-2'} gap-6 pb-4`}>
              {type === 'tools' && (tools as MCPTool[]).map((tool, index) => (
                <div key={index} className={`bg-slate-950/50 border ${colors.cardBorder} rounded-xl p-6 backdrop-blur-sm shadow-[0_0_15px_rgba(0,255,170,0.1)] hover:border-cyan-400/50 transition-colors`}>
                  <div className="mb-4">
                    <h4 className={`font-['Orbitron'] text-lg font-bold ${colors.cardText}`}>{tool.name}</h4>
                  </div>
                  <p className="text-gray-300 text-sm mb-4 leading-relaxed">{tool.description || '暂无描述'}</p>
                  
                  {tool.inputSchema && (
                    <details className="mb-3">
                      <summary className="cursor-pointer text-sm font-medium text-emerald-300 hover:text-emerald-400 transition-colors flex items-center space-x-2">
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M7 16l-4-4m0 0l4-4m-4 4h18"/>
                        </svg>
                        <span>输入参数</span>
                      </summary>
                      <pre className="mt-2 p-3 bg-slate-900/50 border border-emerald-400/20 rounded-lg text-xs text-emerald-200 overflow-x-auto">
                        {formatSchema(tool.inputSchema)}
                      </pre>
                    </details>
                  )}
                  
                  {tool.outputSchema && (
                    <details>
                      <summary className="cursor-pointer text-sm font-medium text-orange-300 hover:text-orange-400 transition-colors flex items-center space-x-2">
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 8l4 4m0 0l-4 4m4-4H3"/>
                        </svg>
                        <span>输出格式</span>
                      </summary>
                      <pre className="mt-2 p-3 bg-slate-900/50 border border-orange-400/20 rounded-lg text-xs text-orange-200 overflow-x-auto">
                        {formatSchema(tool.outputSchema)}
                      </pre>
                    </details>
                  )}
                </div>
              ))}

              {type === 'resources' && (resources as MCPResource[]).map((resource, index) => (
                <div key={index} className={`bg-slate-950/50 border ${colors.cardBorder} rounded-xl p-6 backdrop-blur-sm shadow-[0_0_15px_rgba(139,92,246,0.1)] hover:border-purple-400/50 transition-colors`}>
                  <div className="mb-4">
                    <h4 className={`font-['Orbitron'] text-lg font-bold ${colors.cardText}`}>{resource.name || resource.uri}</h4>
                  </div>
                  <p className="text-gray-300 text-sm mb-4 leading-relaxed">{resource.description || '暂无描述'}</p>
                  
                  <div className="space-y-2 text-sm">
                    <div className="flex items-center space-x-2 text-cyan-300">
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"/>
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"/>
                      </svg>
                      <span className="font-mono text-xs">{resource.uri}</span>
                    </div>
                    {resource.mimeType && (
                      <div className="flex items-center space-x-2 text-emerald-300">
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
                        </svg>
                        <span>{resource.mimeType}</span>
                      </div>
                    )}
                    {resource.size && (
                      <div className="flex items-center space-x-2 text-yellow-300">
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4"/>
                        </svg>
                        <span>{resource.size} bytes</span>
                      </div>
                    )}
                    {resource.annotations?.priority && (
                      <div className="flex items-center space-x-2 text-orange-300">
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z"/>
                        </svg>
                        <span>优先级: {resource.annotations.priority}</span>
                      </div>
                    )}
                  </div>
                </div>
              ))}

              {type === 'prompts' && (prompts as MCPPrompt[]).map((prompt, index) => (
                <div key={index} className={`bg-slate-950/50 border ${colors.cardBorder} rounded-xl p-6 backdrop-blur-sm shadow-[0_0_15px_rgba(59,130,246,0.1)] hover:border-blue-400/50 transition-colors`}>
                  <div className="mb-4">
                    <h4 className={`font-['Orbitron'] text-lg font-bold ${colors.cardText}`}>{prompt.name}</h4>
                  </div>
                  <p className="text-gray-300 text-sm mb-4 leading-relaxed">{prompt.description || '暂无描述'}</p>
                  
                  {prompt.arguments && prompt.arguments.length > 0 && (
                    <details className="mb-3">
                      <summary className="cursor-pointer text-sm font-medium text-cyan-300 hover:text-cyan-400 transition-colors flex items-center space-x-2">
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
                        </svg>
                        <span>参数列表</span>
                      </summary>
                      <div className="mt-3 space-y-2">
                        {prompt.arguments.map((arg, argIndex) => (
                          <div key={argIndex} className="p-3 bg-slate-900/50 border border-cyan-400/20 rounded-lg">
                            <div className="flex items-center space-x-2 mb-1">
                              <span className="font-mono text-cyan-300 text-sm">{arg.name}</span>
                              {arg.required && (
                                <span className="px-2 py-1 text-xs bg-red-500/20 text-red-300 rounded border border-red-500/50">
                                  必需
                                </span>
                              )}
                            </div>
                            {arg.description && (
                              <p className="text-gray-400 text-xs mt-1">{arg.description}</p>
                            )}
                          </div>
                        ))}
                      </div>
                    </details>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center h-64 text-center">
              <div className="w-16 h-16 bg-gradient-to-r from-gray-600/20 to-gray-700/20 rounded-xl flex items-center justify-center mb-4">
                {config.icon}
              </div>
              <h4 className="font-['Orbitron'] text-lg text-gray-300 mb-2">暂无可用内容</h4>
              <p className="text-gray-500 text-sm">{config.emptyMessage}</p>
            </div>
                      )}
          </div>
          
          {/* 滚动指示器 */}
          <div className="absolute top-0 right-0 w-1 h-full bg-gradient-to-b from-transparent via-gray-600/20 to-transparent pointer-events-none"></div>
        </div>
      </div>
    </div>
  );

  return createPortal(modalContent, document.body);
};

export default ServerDetailsModal;
