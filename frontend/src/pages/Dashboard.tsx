import React from 'react';
import { useAuth } from '../context/AuthContext';
import { BookOpen, FileText, Sparkles, Target, Activity } from 'lucide-react';

export const Dashboard: React.FC = () => {
  const { user } = useAuth();

  return (
    <div className="max-w-6xl mx-auto space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">
          Welcome back, <span className="text-indigo-400">{user?.fullName}</span> 👋
        </h1>
        <p className="text-slate-400 mt-1">Here is your academic and project overview.</p>
      </div>

      {/* Stats Quick Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="p-5 bg-slate-900 border border-slate-800 rounded-xl flex items-center space-x-4">
          <div className="p-3 bg-indigo-500/10 text-indigo-400 rounded-lg">
            <FileText className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Documents</p>
            <p className="text-2xl font-bold mt-0.5">0</p>
          </div>
        </div>

        <div className="p-5 bg-slate-900 border border-slate-800 rounded-xl flex items-center space-x-4">
          <div className="p-3 bg-purple-500/10 text-purple-400 rounded-lg">
            <BookOpen className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Study Plans</p>
            <p className="text-2xl font-bold mt-0.5">0</p>
          </div>
        </div>

        <div className="p-5 bg-slate-900 border border-slate-800 rounded-xl flex items-center space-x-4">
          <div className="p-3 bg-emerald-500/10 text-emerald-400 rounded-lg">
            <Target className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Aptitude Score</p>
            <p className="text-2xl font-bold mt-0.5">--</p>
          </div>
        </div>

        <div className="p-5 bg-slate-900 border border-slate-800 rounded-xl flex items-center space-x-4">
          <div className="p-3 bg-amber-500/10 text-amber-400 rounded-lg">
            <Activity className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Active Streak</p>
            <p className="text-2xl font-bold mt-0.5">1 Day</p>
          </div>
        </div>
      </div>

      {/* RAG MVP Spotlight */}
      <div className="p-8 bg-gradient-to-r from-indigo-950/60 to-purple-950/60 border border-indigo-900/50 rounded-2xl relative overflow-hidden">
        <div className="max-w-2xl">
          <div className="inline-flex items-center space-x-2 px-3 py-1 bg-indigo-500/20 border border-indigo-500/30 rounded-full text-indigo-300 text-xs font-semibold mb-4">
            <Sparkles className="w-3.5 h-3.5" />
            <span>Phase 2 & 3 Next Step</span>
          </div>
          <h2 className="text-2xl font-bold">Personal Document-Grounded RAG</h2>
          <p className="text-slate-300 mt-2 leading-relaxed text-sm">
            Upload your course PDFs, notes, or research papers. GenZ AI will chunk, embed with <code className="text-indigo-300">pgvector</code>, and answer your queries with exact page citations.
          </p>
        </div>
      </div>
    </div>
  );
};
