import React, { useState } from 'react';
import type { MCPToolResult } from '../../types/chat';

interface ToolResultDisplayProps {
  toolResult: MCPToolResult;
}

const ToolResultDisplay: React.FC<ToolResultDisplayProps> = ({ toolResult }) => {
  const [isExpanded, setIsExpanded] = useState(false);

  const formatExecutionTime = (time?: number) => {
    if (!time) return '';
    return time < 1000 ? `${time}ms` : `${(time / 1000).toFixed(2)}s`;
  };

  const formatResult = (result: string) => {
    try {
      // 尝试解析为 JSON 并格式化显示
      const parsed = JSON.parse(result);
      return JSON.stringify(parsed, null, 2);
    } catch {
      // 如果不是 JSON，直接返回原文本
      return result;
    }
  };

  const getStatusIcon = () => {
    if (toolResult.success) {
      return <span className="status-icon success">✅</span>;
    } else {
      return <span className="status-icon error">❌</span>;
    }
  };

  const getStatusClass = () => {
    return toolResult.success ? 'success' : 'error';
  };

  return (
    <div className={`tool-result ${getStatusClass()}`}>
      <div 
        className="tool-result-header"
        onClick={() => setIsExpanded(!isExpanded)}
      >
        <div className="tool-info">
          {getStatusIcon()}
          <span className="tool-name">{toolResult.toolName}</span>
          <span className="server-name">@{toolResult.serverName}</span>
          {toolResult.executionTime && (
            <span className="execution-time">
              <span>⏱️</span>
              {formatExecutionTime(toolResult.executionTime)}
            </span>
          )}
        </div>
        <button className="expand-button">
          {isExpanded ? (
            <span>🔽</span>
          ) : (
            <span>▶️</span>
          )}
        </button>
      </div>

      {isExpanded && (
        <div className="tool-result-content">
          {toolResult.success && toolResult.result && (
            <div className="result-section">
              <h5>执行结果</h5>
              <pre className="result-text">
                {formatResult(toolResult.result)}
              </pre>
            </div>
          )}

          {!toolResult.success && toolResult.error && (
            <div className="error-section">
              <h5>错误信息</h5>
              <pre className="error-text">
                {toolResult.error}
              </pre>
            </div>
          )}

          {!toolResult.result && !toolResult.error && (
            <div className="no-content">
              <span>无返回内容</span>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default ToolResultDisplay;