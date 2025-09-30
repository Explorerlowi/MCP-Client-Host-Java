import React, { useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '../ui/dialog';
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
      "description": "Tavily 搜索工具",
      "timeout": 60
    },
    "weather-mcp": {
      "command": "npx",
      "args": [
        "weather-mcp-server"
      ],
      "env": {
        "WEATHER_API_KEY": "your-weather-api-key"
      },
      "disabled": false,
      "description": "天气查询工具",
      "timeout": 30
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

  const handleClose = () => {
    if (!isLoading) {
      setJsonConfig('');
      setError('');
      onOpenChange(false);
    }
  };

  // 处理文件导入
  const handleFileImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // 检查文件类型
    if (!file.name.endsWith('.json')) {
      setError('请选择 JSON 文件');
      return;
    }

    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const content = event.target?.result as string;
        // 验证JSON格式
        JSON.parse(content);
        setJsonConfig(content);
        setError('');
      } catch (err) {
        setError('文件内容不是有效的JSON格式');
      }
    };
    reader.onerror = () => {
      setError('读取文件失败');
    };
    reader.readAsText(file);

    // 重置input，允许重复选择同一文件
    e.target.value = '';
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-4xl max-h-[90vh] overflow-hidden flex flex-col bg-slate-950/95 border border-cyan-400/30 shadow-[0_0_30px_rgba(0,255,170,0.2)]">
        <DialogHeader>
          <DialogTitle className="text-cyan-300 font-['Orbitron'] text-xl">
            JSON 配置导入
          </DialogTitle>
        </DialogHeader>
        
        <div className="flex-1 overflow-y-auto space-y-6 pr-2">
          <div className="space-y-3">
            <p className="text-slate-300 text-sm leading-relaxed">
              支持导入标准的 MCP 服务器配置格式。您可以粘贴 JSON 配置或从文件导入。
            </p>

            <div className="flex gap-2 flex-wrap">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleUseExample}
                disabled={isLoading}
                className="border-cyan-400/30 text-cyan-300 hover:bg-cyan-400/10"
              >
                使用示例配置
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => document.getElementById('file-input')?.click()}
                disabled={isLoading}
                className="border-emerald-400/30 text-emerald-300 hover:bg-emerald-400/10"
              >
                <svg className="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"/>
                </svg>
                从文件导入
              </Button>
              <input
                id="file-input"
                type="file"
                accept=".json"
                onChange={handleFileImport}
                className="hidden"
              />
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleClear}
                disabled={isLoading}
                className="border-red-400/30 text-red-300 hover:bg-red-400/10"
              >
                清空
              </Button>
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="jsonConfig" className="text-slate-300 font-medium">
                JSON 配置 *
              </Label>
              <Textarea
                id="jsonConfig"
                value={jsonConfig}
                onChange={(e) => setJsonConfig(e.target.value)}
                placeholder="请粘贴您的 MCP 服务器 JSON 配置..."
                disabled={isLoading}
                className={`min-h-[400px] font-mono text-sm bg-slate-900/50 border-slate-600/50 text-slate-200 placeholder-slate-500 focus:border-cyan-400/50 focus:ring-cyan-400/20 ${
                  error ? 'border-red-400/50' : ''
                }`}
              />
              {error && (
                <p className="text-red-400 text-sm">{error}</p>
              )}
            </div>

            <div className="flex justify-end gap-3 pt-4 border-t border-slate-700/50">
              <Button
                type="button"
                variant="outline"
                onClick={handleClose}
                disabled={isLoading}
                className="border-slate-600/50 text-slate-300 hover:bg-slate-700/50"
              >
                取消
              </Button>
              <Button
                type="submit"
                disabled={isLoading || !jsonConfig.trim()}
                className="bg-cyan-500/20 hover:bg-cyan-500/30 border border-cyan-500/50 text-cyan-300 shadow-[0_0_10px_rgba(0,255,170,0.3)]"
              >
                {isLoading ? '导入中...' : '导入配置'}
              </Button>
            </div>
          </form>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default JsonImportForm;
