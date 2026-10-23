import React, { useEffect, useRef, useState } from 'react';
import { AlertCircle, RefreshCw, Copy, Check } from 'lucide-react';

interface StrictMermaidProps {
  chartText: string;
  sectionName?: string;
  onRegenerate?: (section: string) => void;
}

export const StrictMermaid: React.FC<StrictMermaidProps> = ({
  chartText,
  sectionName = 'diagram',
  onRegenerate,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [svgContent, setSvgContent] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [copied, setCopied] = useState<boolean>(false);

  useEffect(() => {
    let isMounted = true;
    setLoading(true);
    setError(null);
    setSvgContent(null);

    const renderChart = async () => {
      try {
        // Lazy-load mermaid to keep initial bundle size minimal
        const mermaidModule = await import('mermaid');
        const mermaid = mermaidModule.default;

        mermaid.initialize({
          startOnLoad: false,
          securityLevel: 'strict', // Strict security level: untrusted LLM input cannot inject XSS
          theme: 'dark',
          themeVariables: {
            darkMode: true,
            background: '#020617',
            primaryColor: '#6366f1',
            primaryTextColor: '#f8fafc',
            primaryBorderColor: '#818cf8',
            lineColor: '#94a3b8',
            secondaryColor: '#a855f7',
            tertiaryColor: '#1e293b',
          },
        });

        const id = 'mermaid-' + Math.random().toString(36).substring(2, 9);
        const { svg } = await mermaid.render(id, chartText);
        if (isMounted) {
          setSvgContent(svg);
          setLoading(false);
        }
      } catch (err: any) {
        if (isMounted) {
          console.error('Mermaid rendering failed:', err);
          setError(err?.message || 'Syntax error in Mermaid diagram');
          setLoading(false);
        }
      }
    };

    if (chartText && chartText.trim()) {
      renderChart();
    } else {
      setError('Empty diagram definition.');
      setLoading(false);
    }

    return () => {
      isMounted = false;
    };
  }, [chartText]);

  const handleCopy = () => {
    navigator.clipboard.writeText(chartText);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (loading) {
    return (
      <div className="p-8 flex items-center justify-center space-x-3 text-sm text-slate-400 bg-slate-950/60 rounded-xl border border-slate-800">
        <RefreshCw className="w-4 h-4 animate-spin text-indigo-400" />
        <span>Rendering diagram (strict mode)...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6 bg-slate-900 border border-rose-900/40 rounded-xl space-y-4">
        <div className="flex items-start justify-between">
          <div className="flex items-center space-x-2 text-rose-400 text-sm font-semibold">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>Mermaid Render Error: {error}</span>
          </div>
          {onRegenerate && (
            <button
              onClick={() => onRegenerate(sectionName)}
              className="flex items-center space-x-1.5 px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-semibold transition-colors"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span>Regenerate this section</span>
            </button>
          )}
        </div>

        <div>
          <p className="text-xs text-slate-400 mb-1.5">Raw Mermaid Source:</p>
          <pre className="p-4 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-300 font-mono overflow-x-auto whitespace-pre-wrap">
            {chartText}
          </pre>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-slate-950/80 border border-slate-800 rounded-xl p-6 relative overflow-hidden group">
      <div className="absolute top-4 right-4 z-10 flex items-center space-x-2 opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          onClick={handleCopy}
          className="p-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs flex items-center space-x-1 border border-slate-700"
          title="Copy Mermaid source"
        >
          {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
        </button>
        {onRegenerate && (
          <button
            onClick={() => onRegenerate(sectionName)}
            className="p-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs flex items-center space-x-1 border border-slate-700"
            title="Regenerate diagram"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        )}
      </div>

      <div
        ref={containerRef}
        className="w-full flex justify-center items-center overflow-x-auto select-none [&>svg]:max-w-full [&>svg]:h-auto"
        dangerouslySetInnerHTML={{ __html: svgContent || '' }}
      />
    </div>
  );
};
