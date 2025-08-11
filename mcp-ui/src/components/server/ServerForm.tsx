import React, { useState, useEffect } from 'react';
import type { McpServerSpec, ServerFormData } from '../../types/server';
import { TransportType } from '../../types/server';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '../ui/dialog';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { Textarea } from '../ui/textarea';
import { Label } from '../ui/label';
import { Switch } from '../ui/switch';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select';

interface ServerFormProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  server?: McpServerSpec | null;
  onSave: (server: McpServerSpec) => void;
  isLoading: boolean;
}

const ServerForm: React.FC<ServerFormProps> = ({
  open,
  onOpenChange,
  server,
  onSave,
  isLoading
}) => {
  const [formData, setFormData] = useState<ServerFormData>({
    id: '',
    name: '',
    description: '',
    transport: 'stdio' as TransportType,  
    url: '',
    command: '',
    args: '',
    env: '',
    disabled: false
  });

  const [errors, setErrors] = useState<Record<string, string>>({});

  // 初始化表单数据
  useEffect(() => {
    if (server) {
      setFormData({
        id: server.id,
        name: server.name || '',
        description: server.description || '',
        transport: server.transport,
        url: server.url || '',
        command: server.command || '',
        args: server.args ? server.args.join('\n') : '',
        env: server.env ? Object.entries(server.env).map(([key, value]) => `${key}=${value}`).join('\n') : '',
        disabled: server.disabled
      });
    } else {
      // 重置表单
      setFormData({
        id: '',
        name: '',
        description: '',
        transport: 'stdio' as TransportType,      
        url: '',
        command: '',
        args: '',
        env: '',
        disabled: false
      });
    }
    setErrors({});
  }, [server]);

  // 处理输入变化
  const handleInputChange = (field: keyof ServerFormData, value: string | boolean) => {
    setFormData(prev => ({
      ...prev,
      [field]: value
    }));
    
    // 清除对应字段的错误
    if (errors[field]) {
      setErrors(prev => ({
        ...prev,
        [field]: ''
      }));
    }
  };

  // 验证表单
  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!formData.id.trim()) {
      newErrors.id = '服务器ID不能为空';
    } else if (!/^[a-zA-Z0-9_-]+$/.test(formData.id)) {
      newErrors.id = '服务器ID只能包含字母、数字、下划线和连字符';
    }

    if (!formData.name.trim()) {
      newErrors.name = '服务器名称不能为空';
    }

    // 根据传输类型验证必需字段
    if (formData.transport === TransportType.STDIO) {
      if (!formData.command.trim()) {
        newErrors.command = 'STDIO 传输需要指定命令';
      }
    } else if (formData.transport === TransportType.SSE || formData.transport === TransportType.STREAMABLEHTTP) {
      if (!formData.url.trim()) {
        newErrors.url = 'HTTP 传输需要指定 URL';
      } else if (!/^https?:\/\/.+/.test(formData.url)) {
        newErrors.url = 'URL 格式不正确';
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  // 处理提交
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!validateForm()) {
      return;
    }

    // 转换表单数据为服务器配置
    const serverSpec: McpServerSpec = {
      id: formData.id.trim(),
      name: formData.name.trim(),
      description: formData.description.trim(),
      transport: formData.transport,
      url: formData.url.trim() || undefined,
      command: formData.command.trim() || undefined,
      args: formData.args.trim() ? formData.args.split('\n').map(arg => arg.trim()).filter(arg => arg) : undefined,
      env: formData.env.trim() ? 
        Object.fromEntries(
          formData.env.split('\n')
            .map(line => line.trim())
            .filter(line => line && line.includes('='))
            .map(line => {
              const [key, ...valueParts] = line.split('=');
              return [key.trim(), valueParts.join('=').trim()];
            })
        ) : undefined,
      disabled: formData.disabled
    };

    onSave(serverSpec);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
        {/* 顶部装饰条 */}
        <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-cyan-400 via-emerald-400 to-cyan-400 shadow-[0_0_10px_rgba(0,255,170,0.6)]"></div>
        
        <DialogHeader className="pb-6">
          <DialogTitle className="flex items-center space-x-3">
            <div className="w-8 h-8 bg-gradient-to-r from-cyan-400 to-emerald-400 rounded-lg shadow-[0_0_15px_rgba(0,255,170,0.5)] flex items-center justify-center">
              <svg className="w-5 h-5 text-black" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h6l2 2h6a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h-2m-4 0h-2m-4 0h-2"/>
              </svg>
            </div>
            <span>{server ? '编辑服务器配置' : '添加新服务器'}</span>
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-6">
          {/* 基本信息 */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="id">服务器ID *</Label>
              <Input
                id="id"
                value={formData.id}
                onChange={(e) => handleInputChange('id', e.target.value)}
                disabled={!!server || isLoading}
                className={errors.id ? 'border-red-500/50 bg-red-500/10 focus-visible:ring-red-500' : ''}
                placeholder="例如: amap-maps"
              />
              {errors.id && (
                <div className="text-red-400 text-sm flex items-center space-x-2">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L3.732 16.5c-.77.833.192 2.5 1.732 2.5z"/>
                  </svg>
                  <span>{errors.id}</span>
                </div>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="name">服务器名称 *</Label>
              <Input
                id="name"
                value={formData.name}
                onChange={(e) => handleInputChange('name', e.target.value)}
                disabled={isLoading}
                className={errors.name ? 'border-red-500/50 bg-red-500/10 focus-visible:ring-red-500' : ''}
                placeholder="例如: 高德地图服务"
              />
              {errors.name && (
                <div className="text-red-400 text-sm flex items-center space-x-2">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L3.732 16.5c-.77.833.192 2.5 1.732 2.5z"/>
                  </svg>
                  <span>{errors.name}</span>
                </div>
              )}
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="description">描述</Label>
            <Input
              id="description"
              value={formData.description}
              onChange={(e) => handleInputChange('description', e.target.value)}
              disabled={isLoading}
              placeholder="服务器功能描述"
            />
          </div>

          {/* 传输协议和启用状态 */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="transport">传输协议 *</Label>
              <Select
                value={formData.transport}
                onValueChange={(value) => handleInputChange('transport', value as TransportType)}
                disabled={isLoading}
              >
                <SelectTrigger>
                  <SelectValue placeholder="选择传输协议" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={TransportType.STDIO}>STDIO</SelectItem>
                  <SelectItem value={TransportType.SSE}>SSE</SelectItem>
                  <SelectItem value={TransportType.STREAMABLEHTTP}>HTTP流</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label>启用服务器</Label>
              <div className="flex items-center space-x-2 pt-2">
                <Switch
                  checked={!formData.disabled}
                  onCheckedChange={(checked) => handleInputChange('disabled', !checked)}
                  disabled={isLoading}
                />
                <span className="text-sm text-cyan-200/80">
                  {!formData.disabled ? '已启用' : '已禁用'}
                </span>
              </div>
            </div>
          </div>

          {/* URL配置（SSE/HTTP流传输） */}
          {(formData.transport === TransportType.SSE || formData.transport === TransportType.STREAMABLEHTTP) && (
            <div className="space-y-2">
              <Label htmlFor="url">服务器URL *</Label>
              <Input
                type="url"
                id="url"
                value={formData.url}
                onChange={(e) => handleInputChange('url', e.target.value)}
                disabled={isLoading}
                className={errors.url ? 'border-red-500/50 bg-red-500/10 focus-visible:ring-red-500' : ''}
                placeholder="https://example.com/mcp"
              />
              {errors.url && (
                <div className="text-red-400 text-sm flex items-center space-x-2">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L3.732 16.5c-.77.833.192 2.5 1.732 2.5z"/>
                  </svg>
                  <span>{errors.url}</span>
                </div>
              )}
            </div>
          )}

          {/* STDIO传输配置 */}
          {formData.transport === TransportType.STDIO && (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="command">启动命令 *</Label>
                <Input
                  id="command"
                  value={formData.command}
                  onChange={(e) => handleInputChange('command', e.target.value)}
                  disabled={isLoading}
                  className={errors.command ? 'border-red-500/50 bg-red-500/10 focus-visible:ring-red-500' : ''}
                  placeholder="例如: npx"
                />
                {errors.command && (
                  <div className="text-red-400 text-sm flex items-center space-x-2">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L3.732 16.5c-.77.833.192 2.5 1.732 2.5z"/>
                    </svg>
                    <span>{errors.command}</span>
                  </div>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="args">命令参数（每行一个）</Label>
                <Textarea
                  id="args"
                  value={formData.args}
                  onChange={(e) => handleInputChange('args', e.target.value)}
                  disabled={isLoading}
                  rows={3}
                  placeholder="-y&#10;@amap/amap-maps-mcp-server"
                  className="font-mono text-sm"
                />
              </div>
            </div>
          )}

          {/* 环境变量配置 */}
          <div className="space-y-2">
            <Label htmlFor="env">环境变量（每行一个，格式：KEY=VALUE）</Label>
            <Textarea
              id="env"
              value={formData.env}
              onChange={(e) => handleInputChange('env', e.target.value)}
              disabled={isLoading}
              rows={3}
              placeholder="AMAP_MAPS_API_KEY=your_api_key&#10;DEBUG=true"
              className="font-mono text-sm"
            />
          </div>

          {/* 底部按钮 */}
          <div className="flex justify-end space-x-3 pt-6 border-t border-cyan-400/20">
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
              {isLoading ? '保存中...' : '保存'}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default ServerForm;
