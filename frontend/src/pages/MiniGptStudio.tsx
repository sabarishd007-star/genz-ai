import React, { useState, useRef, useEffect } from 'react';
import {
  Sparkles,
  Cpu,
  Zap,
  Play,
  Square,
  Copy,
  Trash2,
  Check,
  Sliders,
  Flame,
  Layers,
  Activity,
  Terminal,
} from 'lucide-react';

interface Telemetry {
  status: string;
  model_name: string;
  parameters: string;
  device: string;
  gpu_name: string;
  vram_allocated_mb: number;
  context_window: number;
}

const PRESETS = [
  {
    title: 'Cozy Little House',
    prompt: 'Once upon a time, in a cozy little house,',
  },
  {
    title: 'The Brave Puppy',
    prompt: 'One sunny morning, a brave little puppy named Max found a mysterious shiny key in the garden.',
  },
  {
    title: 'Magic Forest',
    prompt: 'Lily walked deep into the magical forest where the trees whispered secrets to the stars.',
  },
  {
    title: 'The Lost Robot',
    prompt: 'In a bustling tech workshop, a tiny friendly robot turned on for the first time.',
  },
];

export const MiniGptStudio: React.FC = () => {
  const [prompt, setPrompt] = useState('Once upon a time, in a cozy little house,');
  const [output, setOutput] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [copied, setCopied] = useState(false);

  // Sampling Hyperparameters
  const [temperature, setTemperature] = useState(0.8);
  const [topK, setTopK] = useState(50);
  const [topP, setTopP] = useState(0.9);
  const [maxTokens, setMaxTokens] = useState(150);
  const [repetitionPenalty, setRepetitionPenalty] = useState(1.1);

  // Live Performance Telemetry
  const [speed, setSpeed] = useState<number>(0);
  const [tokenCount, setTokenCount] = useState<number>(0);
  const [telemetry, setTelemetry] = useState<Telemetry | null>(null);

  const abortControllerRef = useRef<AbortController | null>(null);
  const outputEndRef = useRef<HTMLDivElement>(null);

  // Fetch Hardware & Model Telemetry from ai-service
  useEffect(() => {
    const fetchTelemetry = async () => {
      try {
        const res = await fetch('http://localhost:8000/api/minigpt/status');
        if (res.ok) {
          const data = await res.json();
          setTelemetry(data);
        }
      } catch (err) {
        console.warn('ai-service telemetry not reachable:', err);
      }
    };

    fetchTelemetry();
    const interval = setInterval(fetchTelemetry, 5000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    outputEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [output]);

  const handleGenerate = async () => {
    if (!prompt.trim() || isGenerating) return;

    setIsGenerating(true);
    setOutput(prompt + ' ');
    setSpeed(0);
    setTokenCount(0);

    const controller = new AbortController();
    abortControllerRef.current = controller;

    try {
      const response = await fetch('http://localhost:8000/api/minigpt/generate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          prompt,
          max_tokens: maxTokens,
          temperature,
          top_k: topK,
          top_p: topP,
          repetition_penalty: repetitionPenalty,
          stream: true,
        }),
        signal: controller.signal,
      });

      if (!response.ok) {
        throw new Error(`Server returned HTTP ${response.status}`);
      }

      if (!response.body) return;
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          const trimmed = line.trim();
          if (trimmed.startsWith('data: ')) {
            const dataStr = trimmed.slice(6);
            if (dataStr === '[DONE]') break;

            try {
              const parsed = JSON.parse(dataStr);
              if (parsed.chunk) {
                setOutput((prev) => prev + parsed.chunk);
              }
              if (parsed.tps !== undefined) setSpeed(parsed.tps);
              if (parsed.count !== undefined) setTokenCount(parsed.count);
            } catch {
              // ignore partial json
            }
          }
        }
      }
    } catch (err: any) {
      if (err.name !== 'AbortError') {
        setOutput((prev) => prev + `\n\n[Generation Error: ${err.message || 'Check ai-service connection'}]`);
      }
    } finally {
      setIsGenerating(false);
      abortControllerRef.current = null;
    }
  };

  const handleStop = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(output);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      {/* Header Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between pb-6 border-b border-slate-800 gap-4">
        <div>
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center text-white shadow-lg shadow-purple-500/20">
              <Cpu className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
                MiniGPT Studio
                <span className="text-xs px-2.5 py-0.5 rounded-full bg-purple-500/10 text-purple-400 border border-purple-500/20 font-medium">
                  From-Scratch LLM
                </span>
              </h1>
              <p className="text-sm text-slate-400">
                16.77M Parameter Autoregressive Decoder • Accelerated with Flash Attention & KV-Caching
              </p>
            </div>
          </div>
        </div>

        {/* Live Hardware Telemetry Pill */}
        <div className="flex items-center gap-3">
          <div className="px-4 py-2 rounded-xl bg-slate-900 border border-slate-800 flex items-center gap-3 shadow-inner">
            <div className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse" />
            <div className="text-xs">
              <p className="font-semibold text-slate-200">
                {telemetry?.gpu_name || 'RTX 3050 Laptop GPU'}
              </p>
              <p className="text-slate-400">
                {telemetry ? `${telemetry.vram_allocated_mb} MB VRAM • ${telemetry.parameters}` : 'CUDA Connected'}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Main Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Input & Controls (5 cols) */}
        <div className="lg:col-span-5 space-y-5">
          {/* Prompt Card */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl space-y-4">
            <div className="flex items-center justify-between">
              <label className="text-sm font-semibold text-slate-200 flex items-center gap-2">
                <Terminal className="w-4 h-4 text-indigo-400" />
                Input Prompt
              </label>
              <span className="text-xs text-slate-500">{prompt.length} chars</span>
            </div>

            <textarea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              rows={4}
              placeholder="Enter a prompt to seed generation..."
              className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 focus:border-indigo-500 transition-all resize-none font-mono"
            />

            {/* Presets */}
            <div className="space-y-2">
              <span className="text-xs font-medium text-slate-400 flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5 text-amber-400" />
                Quick Story Presets:
              </span>
              <div className="grid grid-cols-2 gap-2">
                {PRESETS.map((p) => (
                  <button
                    key={p.title}
                    type="button"
                    onClick={() => setPrompt(p.prompt)}
                    className="text-left px-3 py-2 rounded-lg bg-slate-950 border border-slate-800/80 hover:border-indigo-500/50 text-xs text-slate-300 hover:text-white transition-all truncate"
                  >
                    {p.title}
                  </button>
                ))}
              </div>
            </div>

            {/* Action Buttons */}
            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={handleGenerate}
                disabled={isGenerating || !prompt.trim()}
                className="flex-1 py-3 px-4 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 disabled:opacity-50 text-white font-medium text-sm flex items-center justify-center gap-2 shadow-lg shadow-indigo-600/20 transition-all cursor-pointer"
              >
                <Play className="w-4 h-4 fill-current" />
                {isGenerating ? 'Generating...' : 'Generate Text'}
              </button>

              {isGenerating && (
                <button
                  type="button"
                  onClick={handleStop}
                  className="py-3 px-4 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-medium text-sm flex items-center justify-center gap-1.5 transition-all cursor-pointer shadow-lg shadow-rose-600/20"
                >
                  <Square className="w-4 h-4 fill-current" />
                  Stop
                </button>
              )}
            </div>
          </div>

          {/* Hyperparameters Card */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl space-y-4">
            <h3 className="text-sm font-semibold text-slate-200 flex items-center gap-2">
              <Sliders className="w-4 h-4 text-purple-400" />
              Sampling Parameters
            </h3>

            {/* Temperature */}
            <div className="space-y-1.5">
              <div className="flex justify-between text-xs">
                <span className="text-slate-300 flex items-center gap-1">
                  <Flame className="w-3.5 h-3.5 text-amber-400" /> Temperature
                </span>
                <span className="font-mono text-indigo-400 font-semibold">{temperature.toFixed(2)}</span>
              </div>
              <input
                type="range"
                min="0.1"
                max="2.0"
                step="0.05"
                value={temperature}
                onChange={(e) => setTemperature(parseFloat(e.target.value))}
                className="w-full accent-indigo-500 h-1.5 bg-slate-800 rounded-lg cursor-pointer"
              />
            </div>

            {/* Top-K */}
            <div className="space-y-1.5">
              <div className="flex justify-between text-xs">
                <span className="text-slate-300 flex items-center gap-1">
                  <Layers className="w-3.5 h-3.5 text-blue-400" /> Top-K Filtering
                </span>
                <span className="font-mono text-indigo-400 font-semibold">{topK}</span>
              </div>
              <input
                type="range"
                min="1"
                max="100"
                step="1"
                value={topK}
                onChange={(e) => setTopK(parseInt(e.target.value))}
                className="w-full accent-indigo-500 h-1.5 bg-slate-800 rounded-lg cursor-pointer"
              />
            </div>

            {/* Top-P */}
            <div className="space-y-1.5">
              <div className="flex justify-between text-xs">
                <span className="text-slate-300 flex items-center gap-1">
                  <Activity className="w-3.5 h-3.5 text-emerald-400" /> Top-P (Nucleus)
                </span>
                <span className="font-mono text-indigo-400 font-semibold">{topP.toFixed(2)}</span>
              </div>
              <input
                type="range"
                min="0.1"
                max="1.0"
                step="0.05"
                value={topP}
                onChange={(e) => setTopP(parseFloat(e.target.value))}
                className="w-full accent-indigo-500 h-1.5 bg-slate-800 rounded-lg cursor-pointer"
              />
            </div>

            {/* Max Tokens */}
            <div className="space-y-1.5">
              <div className="flex justify-between text-xs">
                <span className="text-slate-300">Max New Tokens</span>
                <span className="font-mono text-indigo-400 font-semibold">{maxTokens}</span>
              </div>
              <input
                type="range"
                min="20"
                max="400"
                step="10"
                value={maxTokens}
                onChange={(e) => setMaxTokens(parseInt(e.target.value))}
                className="w-full accent-indigo-500 h-1.5 bg-slate-800 rounded-lg cursor-pointer"
              />
            </div>

            {/* Repetition Penalty */}
            <div className="space-y-1.5">
              <div className="flex justify-between text-xs">
                <span className="text-slate-300">Repetition Penalty</span>
                <span className="font-mono text-indigo-400 font-semibold">{repetitionPenalty.toFixed(2)}</span>
              </div>
              <input
                type="range"
                min="1.0"
                max="2.0"
                step="0.05"
                value={repetitionPenalty}
                onChange={(e) => setRepetitionPenalty(parseFloat(e.target.value))}
                className="w-full accent-indigo-500 h-1.5 bg-slate-800 rounded-lg cursor-pointer"
              />
            </div>
          </div>
        </div>

        {/* Right Column: Generated Output & Metrics (7 cols) */}
        <div className="lg:col-span-7 flex flex-col space-y-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl flex-1 flex flex-col">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-purple-400" />
                <span className="text-sm font-semibold text-slate-200">Generated Output</span>
              </div>

              <div className="flex items-center gap-2">
                {output && (
                  <>
                    <button
                      type="button"
                      onClick={handleCopy}
                      className="px-2.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs text-slate-300 flex items-center gap-1.5 transition-colors"
                      title="Copy text"
                    >
                      {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                      {copied ? 'Copied' : 'Copy'}
                    </button>
                    <button
                      type="button"
                      onClick={() => setOutput('')}
                      className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-rose-400 transition-colors"
                      title="Clear output"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </>
                )}
              </div>
            </div>

            {/* Output Display Area */}
            <div className="flex-1 mt-4 p-4 rounded-xl bg-slate-950 border border-slate-800/80 overflow-y-auto min-h-[380px] max-h-[500px] font-sans text-sm text-slate-100 leading-relaxed whitespace-pre-wrap select-text">
              {output ? (
                <>
                  {output}
                  {isGenerating && (
                    <span className="inline-block w-2 h-4 bg-indigo-400 animate-pulse ml-1 rounded-sm align-middle" />
                  )}
                </>
              ) : (
                <div className="h-full flex flex-col items-center justify-center text-slate-500 space-y-2 py-16">
                  <Cpu className="w-8 h-8 text-slate-600 animate-bounce" />
                  <p className="text-xs">Click "Generate Text" to stream tokens from MiniGPT</p>
                </div>
              )}
              <div ref={outputEndRef} />
            </div>

            {/* Telemetry Footer */}
            <div className="mt-4 pt-3 border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
              <div className="flex items-center gap-4">
                <span className="flex items-center gap-1.5">
                  <Zap className="w-3.5 h-3.5 text-amber-400" />
                  Speed: <strong className="text-slate-200 font-mono">{speed > 0 ? `${speed.toFixed(2)} tok/s` : '--'}</strong>
                </span>
                <span>
                  Tokens: <strong className="text-slate-200 font-mono">{tokenCount}</strong>
                </span>
              </div>
              <span className="text-slate-500 text-[11px]">
                Powered by MiniGPT (genzai)
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
