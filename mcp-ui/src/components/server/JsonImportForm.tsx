import React, { useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '../ui/dialog';
import { Button } from '../ui/button';
import { Textarea } from '../ui/textarea';
import { Label } from '../ui/label';

interface JsonImportFormProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onImport: (jsonConfig: string) => void;
  isLoading: boolean;
}

const JsonImportForm: React.FC<JsonImportFormProps> = ({
  open,
  onOpenChange,
  onImport,
  isLoading
}) => {
  const [jsonConfig, setJsonConfig] = useState('');
  const [error, setError] = useState('');

  // 示例配置
  const exampleConfig = `{
  "mcpServers": {
    "tavily-mcp": {
      "command": "npx",
      "args": [
        "-y",
        "tavily-mcp"
      ],
      "env": {
        "TAVILY_API_KEY": "your-api-key-here"
      },
      "disabled": false,
      "autoApprove": []
    }
  }
}`;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!jsonConfig.trim()) {
      setError('请输入JSON配置');
      return;
    }

    try {
      // 验证JSON格式
      JSON.parse(jsonConfig);
      onImport(jsonConfig);
    } catch (err) {
      setError('JSON格式不正确，请检查语法');
    }
  };

  const handleUseExample = () => {
    setJsonConfig(exampleConfig);
    setError('');
  };

  const handleClear = () => {
    setJsonConfig('');
    setError('');
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl h-[80vh] flex flex-col">
        {/* 顶部装饰条 */}
        <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-cyan-400 via-emerald-400 to-cyan-400 shadow-[0_0_10px_rgba(0,255,170,0.6)]"></div>
        
        <DialogHeader className="pb-4">
          <DialogTitle className="flex items-center space-x-3">
            <div className="w-8 h-8 bg-gradient-to-r from-cyan-400 to-emerald-400 rounded-lg shadow-[0_0_15px_rgba(0,255,170,0.5)] flex items-center justify-center">
              <svg className="w-5 h-5 text-black" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10"/>
              </svg>
            </div>
            <span>JSON配置导入</span>
          </DialogTitle>
          <DialogDescription>
            粘贴您的MCP服务器JSON配置，系统将自动解析并添加服务器。
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 flex flex-col space-y-4 overflow-hidden">
          {/* 操作按钮 */}
          <div className="flex space-x-3">
            <Button
              type="button"
              onClick={handleUseExample}
              disabled={isLoading}
              variant="outline"
              className="bg-cyan-500/20 hover:bg-cyan-500/30 border-cyan-500/50 text-cyan-300"
            >
              使用示例配置
            </Button>
            <Button
              type="button"
              onClick={handleClear}
              disabled={isLoading}
              variant="outline"
              className="bg-orange-500/20 hover:bg-orange-500/30 border-orange-500/50 text-orange-300"
            >
              清空
            </Button>
          </div>

          {/* JSON配置区域 */}
          <div className="flex-1 flex flex-col space-y-2">
            <Label htmlFor="jsonConfig">JSON配置 *</Label>
            <Textarea
              id="jsonConfig"
              value={jsonConfig}
              onChange={(e) => setJsonConfig(e.target.value)}
              disabled={isLoading}
              className={`flex-1 resize-none font-mono text-sm ${
                error 
                  ? 'border-red-500/50 bg-red-500/10 focus-visible:ring-red-500' 
                  : 'border-cyan-500/30 bg-slate-900/50 focus-visible:ring-cyan-500'
              }`}
              placeholder="请粘贴您的JSON配置..."
            />
            {error && (
              <div className="text-red-400 text-sm flex items-center space-x-2">
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L3.732 16.5c-.77.833.192 2.5 1.732 2.5z"/>
                </svg>
                <span>{error}</span>
              </div>
            )}
          </div>

          {/* 底部按钮 */}
          <div className="flex justify-end space-x-3 pt-4 border-t border-cyan-400/20">
            <Button
              type="button"
              onClick={() => onOpenChange(false)}
              disabled={isLoading}
              variant="outline"
              className="bg-slate-800/50 hover:bg-slate-700/50 border-slate-600 text-slate-300"
            >
              取消
            </Button>
            <Button
              onClick={handleSubmit}
              disabled={isLoading}
              className="bg-gradient-to-r from-cyan-500 to-emerald-500 hover:from-cyan-600 hover:to-emerald-600 text-black font-medium shadow-[0_0_15px_rgba(0,255,170,0.5)]"
            >
              {isLoading ? '导入中...' : '导入配置'}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default JsonImportForm;
