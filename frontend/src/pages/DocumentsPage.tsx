import React, { useState, useEffect } from 'react';
import { documentService, DocumentDto, ChunkDto } from '../services/documentService';
import ChunkViewerModal from '../components/documents/ChunkViewerModal';
import DocumentUploadModal from '../components/documents/DocumentUploadModal';
import { Search, Plus, Trash2, Eye, FileText, Layers, BookOpen, AlertCircle } from 'lucide-react';

export const DocumentsPage: React.FC = () => {
  const [documents, setDocuments] = useState<DocumentDto[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [subjectFilter, setSubjectFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedDocChunks, setSelectedDocChunks] = useState<ChunkDto[] | null>(null);
  const [activeModalDocName, setActiveModalDocName] = useState<string>('');
  const [isUploadOpen, setIsUploadOpen] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string>('');

  const fetchDocuments = async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const data = await documentService.getDocuments(subjectFilter, searchQuery);
      setDocuments(data);
    } catch (err) {
      console.error('Failed to fetch documents', err);
      setErrorMsg('Unable to reach document server. Please ensure the backend is running.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDocuments();
  }, [subjectFilter]);

  const handleDelete = async (id: number) => {
    if (!window.confirm('Delete document and associated embeddings?')) return;
    try {
      await documentService.deleteDocument(id);
      setDocuments((docs) => docs.filter((d) => d.id !== id));
    } catch (err) {
      console.error(err);
      alert('Failed to delete document.');
    }
  };

  const handleInspectChunks = async (doc: DocumentDto) => {
    try {
      const chunks = await documentService.getChunks(doc.id);
      setSelectedDocChunks(chunks);
      setActiveModalDocName(doc.fileName);
    } catch (err) {
      console.error(err);
      alert('Could not load chunks.');
    }
  };

  const handleUploadSuccess = (newDoc: DocumentDto) => {
    setDocuments((prev) => [newDoc, ...prev]);
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-slate-800/80">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <BookOpen className="w-6 h-6 text-cyan-400" />
            <span>Document Hub</span>
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Organize study materials, inspect vector embeddings, and isolate knowledge per user
          </p>
        </div>
        <button
          onClick={() => setIsUploadOpen(true)}
          className="inline-flex items-center gap-2 bg-cyan-600 hover:bg-cyan-500 text-white px-4 py-2.5 rounded-xl font-medium text-sm transition-all shadow-lg shadow-cyan-600/20 active:scale-95"
        >
          <Plus className="w-4 h-4" />
          <span>Upload Document</span>
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-slate-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Search documents by title... (Press Enter)"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && fetchDocuments()}
            className="w-full bg-slate-900 border border-slate-800 text-slate-100 pl-10 pr-4 py-2.5 rounded-xl text-sm placeholder-slate-500 focus:outline-none focus:border-cyan-500 transition-colors"
          />
        </div>
        <select
          value={subjectFilter}
          onChange={(e) => setSubjectFilter(e.target.value)}
          className="bg-slate-900 border border-slate-800 text-slate-200 px-4 py-2.5 rounded-xl text-sm focus:outline-none focus:border-cyan-500 transition-colors cursor-pointer"
        >
          <option value="ALL">All Subjects</option>
          <option value="Computer Science">Computer Science</option>
          <option value="Mathematics">Mathematics</option>
          <option value="Physics">Physics</option>
          <option value="Chemistry">Chemistry</option>
          <option value="General">General</option>
        </select>
        <button
          onClick={fetchDocuments}
          className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-sm font-medium transition-colors"
        >
          Search
        </button>
      </div>

      {errorMsg && (
        <div className="p-3 bg-rose-500/10 border border-rose-500/20 rounded-xl flex items-center gap-2 text-rose-400 text-xs">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Document Content List */}
      {loading ? (
        <div className="text-slate-400 py-16 text-center text-sm flex flex-col items-center gap-3">
          <div className="w-6 h-6 border-2 border-cyan-500 border-t-transparent rounded-full animate-spin" />
          <span>Loading document catalog...</span>
        </div>
      ) : documents.length === 0 ? (
        <div className="text-slate-400 py-16 text-center border-2 border-dashed border-slate-800/80 rounded-2xl p-8 bg-slate-900/30">
          <FileText className="w-12 h-12 text-slate-600 mx-auto mb-3" />
          <h3 className="text-base font-semibold text-slate-300">No documents found</h3>
          <p className="text-xs text-slate-500 max-w-sm mx-auto mt-1 mb-4">
            Upload your first lecture notes, syllabus, or paper to start semantic search and chunk inspection.
          </p>
          <button
            onClick={() => setIsUploadOpen(true)}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-slate-800 hover:bg-slate-700 text-cyan-400 rounded-lg text-xs font-medium transition-colors"
          >
            <Plus className="w-3.5 h-3.5" />
            Upload Document
          </button>
        </div>
      ) : (
        <div className="grid gap-3.5">
          {documents.map((doc) => (
            <div
              key={doc.id}
              className="bg-slate-900 border border-slate-800/80 hover:border-slate-700/80 p-4.5 rounded-2xl flex flex-col sm:flex-row sm:items-center justify-between gap-4 transition-all shadow-sm group"
            >
              <div className="flex items-start sm:items-center gap-3.5">
                <div className="w-10 h-10 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 shrink-0 mt-0.5 sm:mt-0">
                  <FileText className="w-5 h-5" />
                </div>
                <div>
                  <div className="font-semibold text-slate-200 text-sm group-hover:text-white transition-colors">
                    {doc.fileName}
                  </div>
                  <div className="text-xs text-slate-400 flex flex-wrap items-center gap-2 mt-1">
                    <span className="px-2 py-0.5 rounded-md bg-slate-800 text-slate-300 font-medium">
                      {doc.subject || 'General'}
                    </span>
                    <span>•</span>
                    <span className="flex items-center gap-1 text-slate-400">
                      <Layers className="w-3 h-3 text-cyan-400" />
                      {doc.chunkCount} {doc.chunkCount === 1 ? 'chunk' : 'chunks'}
                    </span>
                    <span>•</span>
                    <span
                      className={`px-2 py-0.5 rounded-md text-xs font-medium ${
                        doc.status === 'INDEXED'
                          ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                          : doc.status === 'PROCESSING'
                          ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                          : 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                      }`}
                    >
                      {doc.status}
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2 self-end sm:self-center">
                <button
                  onClick={() => handleInspectChunks(doc)}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium rounded-lg transition-colors border border-slate-700/50"
                >
                  <Eye className="w-3.5 h-3.5 text-cyan-400" />
                  <span>Inspect Chunks</span>
                </button>
                <button
                  onClick={() => handleDelete(doc.id)}
                  className="inline-flex items-center gap-1 px-2.5 py-1.5 text-xs bg-rose-950/30 hover:bg-rose-900/50 text-rose-400 rounded-lg border border-rose-800/40 transition-colors"
                  title="Delete Document"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                  <span>Delete</span>
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Modal - Inspect Chunks */}
      {selectedDocChunks && (
        <ChunkViewerModal
          fileName={activeModalDocName}
          chunks={selectedDocChunks}
          onClose={() => setSelectedDocChunks(null)}
        />
      )}

      {/* Modal - Upload Document */}
      <DocumentUploadModal
        isOpen={isUploadOpen}
        onClose={() => setIsUploadOpen(false)}
        onUploadSuccess={handleUploadSuccess}
      />
    </div>
  );
};

export default DocumentsPage;
