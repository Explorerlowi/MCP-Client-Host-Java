import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { tomorrow } from 'react-syntax-highlighter/dist/esm/styles/prism';

interface MarkdownRendererProps {
  content: string;
  className?: string;
}

const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ content, className = '' }) => {
  return (
    <div className={`markdown-content ${className}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          // 代码块渲染
          code({ node, className, children, ...props }: any) {
            const inline = !className;
            const match = /language-(\w+)/.exec(className || '');
            return !inline && match ? (
              <SyntaxHighlighter
                style={tomorrow as any}
                language={match[1]}
                PreTag="div"
                className="rounded-lg !bg-slate-950/80 !border !border-cyan-400/20"
                {...props}
              >
                {String(children).replace(/\n$/, '')}
              </SyntaxHighlighter>
            ) : (
              <code 
                className="bg-slate-800/50 text-cyan-300 px-1 py-0.5 rounded text-sm font-mono border border-cyan-400/20" 
                {...props}
              >
                {children}
              </code>
            );
          },
          // 标题渲染
          h1: ({ children }) => (
            <h1 className="text-2xl font-bold text-cyan-300 mb-4 border-b border-cyan-400/30 pb-2">
              {children}
            </h1>
          ),
          h2: ({ children }) => (
            <h2 className="text-xl font-semibold text-cyan-300 mb-3 border-b border-cyan-400/20 pb-1">
              {children}
            </h2>
          ),
          h3: ({ children }) => (
            <h3 className="text-lg font-semibold text-emerald-300 mb-2">
              {children}
            </h3>
          ),
          // 段落渲染
          p: ({ children }) => (
            <p className="mb-3 leading-relaxed">
              {children}
            </p>
          ),
          // 列表渲染
          ul: ({ children }) => (
            <ul className="list-disc list-inside mb-3 space-y-1 text-cyan-100">
              {children}
            </ul>
          ),
          ol: ({ children }) => (
            <ol className="list-decimal list-inside mb-3 space-y-1 text-cyan-100">
              {children}
            </ol>
          ),
          li: ({ children }) => (
            <li className="text-cyan-100/90">
              {children}
            </li>
          ),
          // 引用渲染
          blockquote: ({ children }) => (
            <blockquote className="border-l-4 border-cyan-400/50 pl-4 py-2 mb-3 bg-slate-800/30 text-cyan-200/90 italic">
              {children}
            </blockquote>
          ),
          // 表格渲染
          table: ({ children }) => (
            <div className="overflow-x-auto mb-4">
              <table className="min-w-full border border-cyan-400/30 rounded-lg overflow-hidden">
                {children}
              </table>
            </div>
          ),
          thead: ({ children }) => (
            <thead className="bg-cyan-500/20">
              {children}
            </thead>
          ),
          tbody: ({ children }) => (
            <tbody className="bg-slate-800/30">
              {children}
            </tbody>
          ),
          tr: ({ children }) => (
            <tr className="border-b border-cyan-400/20">
              {children}
            </tr>
          ),
          th: ({ children }) => (
            <th className="px-4 py-2 text-left text-cyan-300 font-semibold">
              {children}
            </th>
          ),
          td: ({ children }) => (
            <td className="px-4 py-2 text-cyan-100/90">
              {children}
            </td>
          ),
          // 链接渲染
          a: ({ children, href }) => (
            <a 
              href={href} 
              target="_blank" 
              rel="noopener noreferrer"
              className="text-cyan-400 hover:text-cyan-300 underline hover:no-underline transition-colors"
            >
              {children}
            </a>
          ),
          // 强调渲染
          strong: ({ children }) => (
            <strong className="font-bold text-emerald-300">
              {children}
            </strong>
          ),
          em: ({ children }) => (
            <em className="italic text-cyan-300">
              {children}
            </em>
          ),
          // 水平线
          hr: () => (
            <hr className="my-4 border-0 h-px bg-gradient-to-r from-transparent via-cyan-400/50 to-transparent" />
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
};

export default MarkdownRenderer;
