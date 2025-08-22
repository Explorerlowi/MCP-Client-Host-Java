import React, { useState } from 'react';
import type { ChatMessage } from '../../types/chat';
import { formatDistanceToNow } from 'date-fns';
import { Badge } from '../ui/badge'
import ToolResultDisplay from './ToolResultDisplay';
import MarkdownRenderer from './MarkdownRenderer';
import CopyButton from './CopyButton';

interface MessageItemProps {
  message: ChatMessage;
  groupMessages?: ChatMessage[]; // 如果提供，则渲染多个气泡
}

const MessageItem: React.FC<MessageItemProps> = ({ message, groupMessages }) => {
  const [showTimestamp, setShowTimestamp] = useState(false);

  const formatTime = (timestamp: Date) => {
    return formatDistanceToNow(new Date(timestamp), { 
      addSuffix: true
    });
  };

  const isAssistant = message.role === 'assistant';
  const [showReasoning, setShowReasoning] = useState(false);

  // 解析消息内容中的工具调用结果
  const parseToolResults = (content: string) => {
    // 查找工具调用结果的模式
    const toolResultPattern = /\[工具调用结果\](.*?)\[\/工具调用结果\]/gs;
    const matches = [];
    let match;
    
    while ((match = toolResultPattern.exec(content)) !== null) {
      try {
        const resultData = JSON.parse(match[1]);
        matches.push(resultData);
      } catch {
        // 如果解析失败，忽略该结果
        console.warn('解析工具调用结果失败:', match[1]);
      }
    }
    
    return matches;
  };

  // 移除内容中的工具调用结果标记，只显示纯文本
  const cleanContent = (content: string) => {
    return content.replace(/\[工具调用结果\].*?\[\/工具调用结果\]/gs, '').trim();
  };


  const messagesToRender = groupMessages || [message];
  
  // 渲染单个气泡
  const renderBubble = (msg: ChatMessage, isLast: boolean, bubbleIndex: number) => {
    const msgToolResults = parseToolResults(msg.content || '');
    const msgDisplayContent = cleanContent(msg.content || '');
    const msgIsError = msg.metadata?.isError;
    const msgHasToolResults = msg.metadata?.toolResults;
    const msgHasReasoning = !!(msg.reasoningContent && msg.reasoningContent.trim());

    return (
      <div 
        key={msg.id}
        className={`relative backdrop-blur-sm border rounded-2xl p-4 shadow-lg mb-2 ${
          message.role === 'user'
            ? 'bg-gradient-to-br from-orange-500/20 to-red-500/20 border-orange-400/30 text-orange-100 shadow-[0_0_20px_rgba(255,107,53,0.15)]'
            : `bg-gradient-to-br from-slate-800/50 to-slate-900/50 border-cyan-400/30 text-cyan-100 shadow-[0_0_20px_rgba(0,255,170,0.15)] ${msgIsError ? 'border-red-400/50 bg-red-900/20' : ''}`
        }`}
      >
        {/* 思考状态徽标 - 只在第一个气泡显示 */}
        {bubbleIndex === 0 && isAssistant && msgHasReasoning && (
          <div className="absolute -top-3 left-4">
            <Badge 
              variant="secondary" 
              className={`text-xs px-2 py-1 ${
                msg.isStreaming 
                  ? 'bg-cyan-400/20 text-cyan-300 border-cyan-400/30 animate-pulse' 
                  : 'bg-emerald-400/20 text-emerald-300 border-emerald-400/30 cursor-pointer hover:bg-emerald-400/30'
              }`}
              onClick={(e) => { e.stopPropagation(); if (!msg.isStreaming) setShowReasoning(v => !v); }}
            >
              {msg.isStreaming ? (
                <>🧠 神经网络处理中...</>
              ) : (
                <>🧠 思考完成 {showReasoning ? '▲' : '▼'}</>
              )}
            </Badge>
          </div>
        )}

        {/* 时间戳 - 只在最后一个气泡显示 */}
        {isLast && showTimestamp && (
          <div className="text-xs opacity-60 mb-2 font-mono">
            {formatTime(msg.timestamp)}
          </div>
        )}
        
        {/* 思考内容 */}
        {(msg.isStreaming || showReasoning) && msg.reasoningContent && (
          <div className="mb-3 p-3 bg-slate-950/50 border border-cyan-400/20 rounded-lg">
            <div className="flex items-center space-x-2 mb-2 text-xs text-cyan-400">
              <div className="w-2 h-2 bg-cyan-400 rounded-full animate-pulse"></div>
              <span>神经网络思考过程</span>
            </div>
            <div className="text-sm text-cyan-200/80 leading-relaxed">
              <MarkdownRenderer content={msg.reasoningContent} className="text-sm" />
            </div>
          </div>
        )}

        {/* 主消息内容 */}
        <div className="leading-relaxed">
          {msgDisplayContent ? <MarkdownRenderer content={msgDisplayContent} /> : null}
        </div>

        {/* 工具调用结果 */}
        {msgToolResults.length > 0 && (
          <div className="mt-4 space-y-3">
            <div className="flex items-center space-x-2 text-sm text-cyan-400">
              <div className="w-2 h-2 bg-cyan-400 rounded-full"></div>
              <span>工具执行报告</span>
            </div>
            {msgToolResults.map((result, index) => (
              <div key={index} className="bg-slate-950/50 border border-cyan-400/20 rounded-lg p-3">
                <ToolResultDisplay toolResult={result} />
              </div>
            ))}
          </div>
        )}

        {/* 额外信息 */}
        {msg.extraContent && msg.extraContent.trim() && (
          <div className="mt-4">
            <div className="flex items-center space-x-2 text-sm text-emerald-400 mb-2">
              <div className="w-2 h-2 bg-emerald-400 rounded-full"></div>
              <span>系统附加信息</span>
            </div>
            <pre className="text-sm bg-slate-950/50 border border-emerald-400/20 rounded-lg p-3 text-emerald-200/80 font-mono whitespace-pre-wrap">
              {msg.extraContent}
            </pre>
          </div>
        )}

        {/* 元数据工具结果 */}
        {msgHasToolResults && (
          <div className="mt-4">
            <div className="flex items-center space-x-2 text-sm text-cyan-400 mb-2">
              <div className="w-2 h-2 bg-cyan-400 rounded-full"></div>
              <span>执行结果</span>
            </div>
            <div className="space-y-2">
              {Array.isArray(msgHasToolResults) ? (
                msgHasToolResults.map((result, index) => (
                  <div key={index} className="bg-slate-950/50 border border-cyan-400/20 rounded-lg p-3">
                    <ToolResultDisplay toolResult={result} />
                  </div>
                ))
              ) : (
                <div className="bg-slate-950/50 border border-cyan-400/20 rounded-lg p-3">
                  <ToolResultDisplay toolResult={msgHasToolResults} />
                </div>
              )}
            </div>
          </div>
        )}

        {/* 复制按钮 - 只在最后一个气泡的左下角显示，且仅对assistant消息 */}
        {isLast && message.role === 'assistant' && (
          <div className="absolute bottom-2 -left-10 opacity-0 group-hover:opacity-100 transition-opacity duration-200">
            <CopyButton messages={messagesToRender} />
          </div>
        )}

        {/* 气泡装饰三角 - 只在最后一个气泡显示 */}
        {isLast && (
          <div className={`absolute top-4 w-0 h-0 ${
            message.role === 'user'
              ? 'right-[-8px] border-l-8 border-l-orange-400/50 border-t-8 border-t-transparent border-b-8 border-b-transparent'
              : 'left-[-8px] border-r-8 border-r-cyan-400/50 border-t-8 border-t-transparent border-b-8 border-b-transparent'
          }`}></div>
        )}
      </div>
    );
  };

  return (
    <div 
      className={`group relative mb-6 ${message.role === 'user' ? 'flex justify-end' : 'flex justify-start'}`}
      onClick={() => setShowTimestamp(!showTimestamp)}
    >
      <div className={`flex items-end space-x-3 max-w-[80%] ${message.role === 'user' ? 'flex-row-reverse' : 'flex-row'}`}>
        {/* 机甲风格头像 - 位于左下角/右下角 */}
        <div className="relative shrink-0">
          <div className={`w-10 h-10 rounded-full flex items-center justify-center text-lg font-bold shadow-lg ${
            message.role === 'user' 
              ? 'bg-gradient-to-r from-orange-400 to-red-400 text-orange-900 shadow-[0_0_20px_rgba(255,107,53,0.4)]' 
              : 'bg-gradient-to-r from-cyan-400 to-emerald-400 text-cyan-900 shadow-[0_0_20px_rgba(0,255,170,0.4)]'
          }`}>
            {message.role === 'user' ? '👤' : '🤖'}
          </div>
          {/* 在线状态指示器 */}
          <div className={`absolute -bottom-1 -right-1 w-4 h-4 rounded-full border-2 border-slate-900 ${
            message.role === 'user' ? 'bg-orange-400' : 'bg-emerald-400'
          } ${message.isStreaming ? 'animate-ping' : ''}`}></div>
        </div>

        {/* 消息气泡组 */}
        <div className="flex flex-col space-y-1">
          {messagesToRender.map((msg, index) => 
            renderBubble(msg, index === messagesToRender.length - 1, index)
          )}
        </div>
      </div>
    </div>
  );
};

export default MessageItem;