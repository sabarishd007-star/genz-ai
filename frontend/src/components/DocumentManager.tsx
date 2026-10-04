import React, { useState, useEffect, useRef } from 'react';
import { uploadDocument, getDocuments, deleteDocument } from '../api/ragApi';
import { Document } from '../types/rag';
import { FileText, Trash2, Upload, CheckCircle2, AlertCircle, Loader2 } from 'lucide-react';

const formatBytes = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const StatusBadge: React.FC<{ status: Document['status'] }> = ({ status }) => {
  if (status === 'indexed')
    return (
      <span className="flex items-center gap-1 text-emerald-400 text-xs font-medium">
        <CheckCircle2 className="w-3 h-3" /> Indexed
      </span>
    );
  if (status === 'processing')
    return (
      <span className="flex items-center gap-1 text-amber-400 text-xs font-medium">
        <Loader2 className="w-3 h-3 animate-spin" /> Processing
      </span>
    );
  return (
    <span className="flex items-center gap-1 text-red-400 text-xs font-medium">
      <AlertCircle className="w-3 h-3" /> Failed
    </span>
  );
};

export const DocumentManager: React.FC<{
  selectedDocIds: string[];
  onToggleSelectDoc: (id: string) => void;
}> = ({ selectedDocIds, onToggleSelectDoc }) => {
  const [documents, setDocuments] = useState<Document[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [dragActive, setDragActive] = useState(false);
  const [error, setError] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    loadDocs();
  }, []);

  const loadDocs = async () => {
    try {
      const data = await getDocuments();
      setDocuments(data);
    } catch {
      // API not yet implemented — show empty state
      setDocuments([]);
    }
  };

  const handleUpload = async (file: File) => {
    if (!file) return;
    setError('');
    setUploading(true);
    setUploadProgress(0);
    try {
      const newDoc = await uploadDocument(file, (p) => setUploadProgress(p));
      setDocuments((prev) => [newDoc, ...prev]);
    } catch {
      setError('Upload failed — RAG backend not yet connected. Coming in Phase 2.');
    } finally {
      setUploading(false);
      setUploadProgress(0);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) handleUpload(file);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragActive(false);
    const file = e.dataTransfer.files?.[0];
    if (file) handleUpload(file);
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteDocument(id);
      setDocuments((prev) => prev.filter((d) => d.id !== id));
    } catch {
      setDocuments((prev) => prev.filter((d) => d.id !== id));
    }
  };

  return (
    <div className="flex flex-col gap-4 h-full">
      <div>
        <h3 className="text-base font-semibold text-white mb-1">Knowledge Base</h3>
        <p className="text-xs text-slate-400">Upload documents to query with AI</p>
      </div>

      {/* Drop Zone */}
      <div
        onDragOver={(e) => { e.preventDefault(); setDragActive(true); }}
        onDragLeave={() => setDragActive(false)}
        onDrop={handleDrop}
        onClick={() => inputRef.current?.click()}
        className={`flex flex-col items-center justify-center border-2 border-dashed rounded-xl p-6 cursor-pointer transition-all duration-200 ${
          dragActive
            ? 'border-indigo-500 bg-indigo-500/10'
            : 'border-slate-700 hover:border-indigo-600 hover:bg-slate-800/50'
        }`}
      >
        <Upload className="w-7 h-7 text-slate-400 mb-2" />
        <p className="text-sm text-slate-300 font-medium">Drop file or click to upload</p>
        <p className="text-xs text-slate-500 mt-1">PDF, TXT, DOCX supported</p>
        <input
          ref={inputRef}
          type="file"
          onChange={handleFileInput}
          accept=".pdf,.txt,.docx"
          className="hidden"
          disabled={uploading}
        />
      </div>

      {/* Upload Progress */}
      {uploading && (
        <div>
          <div className="flex justify-between text-xs text-slate-400 mb-1">
            <span className="flex items-center gap-1"><Loader2 className="w-3 h-3 animate-spin" />Indexing embeddings...</span>
            <span>{uploadProgress}%</span>
          </div>
          <div className="w-full bg-slate-700 h-1.5 rounded-full overflow-hidden">
            <div
              className="bg-indigo-500 h-full transition-all duration-300"
              style={{ width: `${uploadProgress}%` }}
            />
          </div>
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="p-3 bg-amber-950/40 border border-amber-800/50 rounded-lg text-xs text-amber-300">
          {error}
        </div>
      )}

      {/* Document List */}
      <div className="flex-1 space-y-2 overflow-y-auto pr-1">
        {documents.length === 0 && !uploading && (
          <div className="text-center py-8 text-slate-500 text-sm">
            <FileText className="w-10 h-10 mx-auto mb-2 opacity-30" />
            <p>No documents uploaded yet.</p>
            <p className="text-xs mt-1 text-slate-600">Upload a PDF to start querying</p>
          </div>
        )}
        {documents.map((doc) => (
          <div
            key={doc.id}
            className="flex items-start gap-2.5 bg-slate-900 border border-slate-700/60 rounded-lg p-3 group"
          >
            <input
              type="checkbox"
              checked={selectedDocIds.includes(doc.id)}
              onChange={() => onToggleSelectDoc(doc.id)}
              className="mt-0.5 rounded border-slate-600 accent-indigo-500 cursor-pointer"
            />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-slate-200 truncate">{doc.name}</p>
              <div className="flex items-center gap-2 mt-1">
                <span className="text-xs text-slate-500">{formatBytes(doc.size)}</span>
                {doc.chunkCount && (
                  <span className="text-xs text-slate-500">{doc.chunkCount} chunks</span>
                )}
                <StatusBadge status={doc.status} />
              </div>
            </div>
            <button
              onClick={() => handleDelete(doc.id)}
              className="opacity-0 group-hover:opacity-100 p-1 text-slate-500 hover:text-red-400 transition-all"
              title="Delete document"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        ))}
      </div>

      {/* Context Info */}
      {selectedDocIds.length > 0 && (
        <div className="p-2.5 bg-indigo-950/40 border border-indigo-800/40 rounded-lg text-xs text-indigo-300">
          <span className="font-semibold">{selectedDocIds.length}</span> document(s) selected as context
        </div>
      )}
    </div>
  );
};
