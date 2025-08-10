import React, { useMemo } from 'react';
import type { ChatMessage } from '../../types/chat';
import MessageItem from './MessageItem';

interface MessageListProps {
  messages: ChatMessage[];
}

const MessageList: React.FC<MessageListProps> = ({ messages }) => {
  if (messages.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-full relative">
        {/* 全息科技背景装饰 */}
        <div className="absolute inset-0 flex items-center justify-center opacity-20">
          <div className="w-96 h-96 rounded-full border-2 border-cyan-400/30 animate-ping"></div>
          <div className="absolute w-72 h-72 rounded-full border-2 border-emerald-400/20 animate-pulse"></div>
          <div className="absolute w-48 h-48 rounded-full border border-orange-400/10 animate-spin"></div>
        </div>
        
        {/* 主要内容 */}
        <div className="relative z-10 flex flex-col items-center space-y-8 p-8">
          {/* 机甲头部图标 */}
          <div className="relative">
            <div className="w-24 h-24 bg-gradient-to-br from-cyan-400/20 to-emerald-400/20 rounded-2xl backdrop-blur-sm border border-cyan-400/30 flex items-center justify-center shadow-[0_0_40px_rgba(0,255,170,0.3)]">
              <div className="relative">
                {/* 主要图标 */}
                <div className="w-12 h-12 bg-gradient-to-r from-cyan-400 to-emerald-400 rounded-xl shadow-[0_0_20px_rgba(0,255,170,0.6)] flex items-center justify-center">
                  <svg className="w-8 h-8 text-black" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
                  </svg>
                </div>
                {/* 脉冲环 */}
                <div className="absolute -inset-2 rounded-xl border-2 border-cyan-400/50 animate-ping"></div>
                {/* 状态指示灯 */}
                <div className="absolute -top-1 -right-1 w-4 h-4 bg-emerald-400 rounded-full animate-pulse shadow-[0_0_10px_rgba(0,255,170,0.8)]"></div>
              </div>
            </div>
          </div>
          
          {/* 文字内容 */}
          <div className="text-center space-y-4 max-w-md">
            <h3 className="font-['Orbitron'] text-2xl font-bold bg-gradient-to-r from-cyan-300 via-emerald-300 to-cyan-300 bg-clip-text text-transparent">
              开始对话
            </h3>
            <p className="text-cyan-200/70 text-base leading-relaxed">
              向 AI 助手提问，获得智能回答和工具支持
            </p>
            
            {/* 功能提示 */}
            <div className="grid grid-cols-1 gap-3 mt-6 text-sm">
              <div className="flex items-center space-x-3 p-3 bg-cyan-400/5 rounded-lg border border-cyan-400/20 backdrop-blur-sm">
                <div className="w-2 h-2 bg-cyan-400 rounded-full animate-pulse"></div>
                <span className="text-cyan-300/80">支持智能对话交互</span>
              </div>
              <div className="flex items-center space-x-3 p-3 bg-emerald-400/5 rounded-lg border border-emerald-400/20 backdrop-blur-sm">
                <div className="w-2 h-2 bg-emerald-400 rounded-full animate-pulse"></div>
                <span className="text-emerald-300/80">集成多种工具和服务</span>
              </div>
              <div className="flex items-center space-x-3 p-3 bg-orange-400/5 rounded-lg border border-orange-400/20 backdrop-blur-sm">
                <div className="w-2 h-2 bg-orange-400 rounded-full animate-pulse"></div>
                <span className="text-orange-300/80">实时响应和处理</span>
              </div>
            </div>
          </div>
          
          {/* 扫描线动画 */}
          <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-transparent via-cyan-400 to-transparent opacity-60 animate-pulse"></div>
        </div>
      </div>
    );
  }

  // 按角色分组连续消息，支持拆分带 extraContent 的消息
  const groupedMessages = useMemo(() => {
    const groups: { role: string; messages: ChatMessage[]; isGroup: boolean }[] = [];
    
    for (const m of messages) {
      const extra = m.extraContent;
      const hasReasoning = !!(m.reasoningContent && m.reasoningContent.trim());
      
      // 尝试拆分消息
      let subMessages: ChatMessage[] = [];
      if (extra && extra.trim() && hasReasoning) {
        try {
          const parsed = JSON.parse(extra);
          if (Array.isArray(parsed) && parsed.length > 0) {
            const rawR = m.reasoningContent || '';
            const rawC = m.content || '';

            const rStarts: number[] = [0];
            const cStarts: number[] = [0];
            parsed
              .slice()
              .sort((a: any, b: any) => (Number(a?.index ?? 0) - Number(b?.index ?? 0)))
              .forEach((item: any) => {
                const rPos = Number(item?.reasoningPosition);
                const cPos = Number(item?.fullContentPosition);
                if (Number.isFinite(rPos)) rStarts.push(rPos);
                if (Number.isFinite(cPos)) cStarts.push(cPos);
              });
            rStarts.push(rawR.length);
            cStarts.push(rawC.length);
            const uniqSort = (xs: number[]) => Array.from(new Set(xs)).sort((a, b) => a - b);
            const rIdx = uniqSort(rStarts);
            const cIdx = uniqSort(cStarts);
            const segCount = Math.max(rIdx.length - 1, cIdx.length - 1);
            
            if (segCount > 1) {
              for (let i = 0; i < segCount; i++) {
                const reasoning = rawR.slice(rIdx[i] ?? 0, rIdx[i + 1] ?? rawR.length).trim();
                const content = rawC.slice(cIdx[i] ?? 0, cIdx[i + 1] ?? rawC.length).trim();
                if (reasoning || content) {
                  subMessages.push({
                    ...m,
                    id: `${m.id}-seg-${i}`,
                    reasoningContent: reasoning,
                    content: content,
                    extraContent: ''
                  });
                }
              }
            }
          }
        } catch {
          // 解析失败，使用原消息
        }
      }
      
      // 如果没有拆分成功，使用原消息
      if (subMessages.length === 0) {
        subMessages = [m];
      }
      
      // 检查是否可以与上一组合并（同一角色的连续消息）
      const lastGroup = groups[groups.length - 1];
      if (lastGroup && lastGroup.role === m.role && subMessages.length > 1) {
        // 如果当前消息被拆分了，且与上一组是同一角色，合并到上一组
        lastGroup.messages.push(...subMessages);
        lastGroup.isGroup = true;
      } else if (lastGroup && lastGroup.role === m.role && subMessages.length === 1) {
        // 单条消息，且与上一组是同一角色，合并到上一组
        lastGroup.messages.push(...subMessages);
        if (lastGroup.messages.length > 1) {
          lastGroup.isGroup = true;
        }
      } else {
        // 创建新组
        groups.push({
          role: m.role,
          messages: subMessages,
          isGroup: subMessages.length > 1
        });
      }
    }
    
    return groups;
  }, [messages]);

  return (
    <div className="message-list">
      {groupedMessages.map((group, groupIndex) => (
        <MessageItem
          key={`group-${groupIndex}`}
          message={group.messages[0]}
          groupMessages={group.isGroup ? group.messages : undefined}
        />
      ))}
    </div>
  );
};

export default MessageList;