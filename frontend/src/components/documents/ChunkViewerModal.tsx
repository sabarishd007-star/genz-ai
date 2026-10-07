import React from 'react';
import { ChunkDto } from '../../services/documentService';
import { X, Copy, Check, Layers, Cpu } from 'lucide-react';

interface ChunkViewerModalProps {
  fileName: string;
  chunks: ChunkDto[];
  onClose: () => void;
}

export const ChunkViewerModal: React.FC<ChunkViewerModalProps> = ({
  fileName,
  chunks,
  onClose,
}) => {
  const [copiedIndex, setCopiedIndex] = React.useState<number | null>(null);

  const handleCopy = (content: string, index: number) => {
    navigator.clipboard.writeText(content);
    setCopiedIndex(index);
    setTimeout(() => setCopiedIndex(null), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 sm:p-6 animate-in fade-in duration-200">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-4xl max-h-[85vh] flex flex-col shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between bg-slate-900/90 sticky top-0 z-10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center text-cyan-400">
              <Layers className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-semibold text-lg text-slate-100 flex items-center gap-2">
                <span>Vector Embeddings Inspection</span>
                <span className="text-xs px-2.5 py-0.5 rounded-full bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 font-mono">
                  {chunks.length} chunks
                </span>
              </h3>
              <p className="text-xs text-slate-400 truncate max-w-md">{fileName}</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-slate-400 hover:text-slate-200 hover:bg-slate-800 rounded-lg transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Chunks List */}
        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          {chunks.length === 0 ? (
            <div className="text-center py-16 text-slate-500 text-sm">
              No vector chunks recorded for this document yet.
            </div>
          ) : (
            chunks.map((chunk) => (
              <div
                key={chunk.id || chunk.chunkIndex}
                className="bg-slate-950/70 border border-slate-800/80 rounded-xl p-4 transition-all hover:border-slate-700/80"
              >
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-mono px-2 py-0.5 bg-slate-800 text-slate-300 rounded-md">
                      Chunk #{chunk.chunkIndex + 1}
                    </span>
                    <span className="text-xs flex items-center gap-1 text-slate-400">
                      <Cpu className="w-3 h-3 text-cyan-400" />
                      {chunk.tokenCount} tokens
                    </span>
                  </div>
                  <button
                    onClick={() => handleCopy(chunk.content, chunk.chunkIndex)}
                    className="flex items-center gap-1 text-xs text-slate-400 hover:text-cyan-400 px-2 py-1 rounded hover:bg-slate-800 transition-colors"
                  >
                    {copiedIndex === chunk.chunkIndex ? (
                      <>
                        <Check className="w-3.5 h-3.5 text-emerald-400" />
                        <span className="text-emerald-400">Copied</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-3.5 h-3.5" />
                        <span>Copy</span>
                      </>
                    )}
                  </button>
                </div>
                <div className="text-sm font-sans text-slate-200 leading-relaxed whitespace-pre-wrap bg-slate-900/50 p-3.5 rounded-lg border border-slate-800/50 font-mono text-xs">
                  {chunk.content}
                </div>
              </div>
            ))
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-3 border-t border-slate-800 bg-slate-900/70 flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg transition-colors font-medium"
          >
            Close Viewer
          </button>
        </div>
      </div>
    </div>
  );
};

export default ChunkViewerModal;
