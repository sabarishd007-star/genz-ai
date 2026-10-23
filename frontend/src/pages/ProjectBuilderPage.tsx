import React, { useState, useEffect } from 'react';
import {
  Code,
  Layers,
  CheckCircle2,
  HelpCircle,
  Download,
  RefreshCw,
  Plus,
  AlertTriangle,
  FileText,
  Terminal,
  Database,
  ArrowRight,
  Sparkles,
  BookOpen,
  Send,
  Sliders,
  Check,
  Zap,
} from 'lucide-react';
import { projectApi, ProjectDto, VivaQuestionDto, VivaAnswerResponse } from '../api/projectApi';
import { documentService, DocumentDto } from '../services/documentService';
import { StrictMermaid } from '../components/project/StrictMermaid';
import { RoadmapGraph } from '../components/project/RoadmapGraph';

export const ProjectBuilderPage: React.FC = () => {
  // Projects state
  const [projects, setProjects] = useState<ProjectDto[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);
  const [currentProject, setCurrentProject] = useState<ProjectDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [backendOffline, setBackendOffline] = useState<boolean>(false);

  // Tab state
  const [activeTab, setActiveTab] = useState<
    'overview' | 'features' | 'stack' | 'er' | 'api' | 'architecture' | 'roadmap' | 'files' | 'viva'
  >('overview');

  // New Project Form Modal / Toggle
  const [isCreating, setIsCreating] = useState<boolean>(false);
  const [createTitle, setCreateTitle] = useState<string>('');
  const [createDomain, setCreateDomain] = useState<string>('Full-stack web');
  const [createIdea, setCreateIdea] = useState<string>('');
  const [createConstraints, setCreateConstraints] = useState<string>('{}');
  const [selectedDocId, setSelectedDocId] = useState<string>('');
  const [availableDocs, setAvailableDocs] = useState<DocumentDto[]>([]);
  const [createSubmitting, setCreateSubmitting] = useState<boolean>(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // Viva Practice State
  const [vivaQuestions, setVivaQuestions] = useState<VivaQuestionDto[]>([]);
  const [selectedVivaId, setSelectedVivaId] = useState<string | null>(null);
  const [vivaAnswerText, setVivaAnswerText] = useState<string>('');
  const [vivaSubmitting, setVivaSubmitting] = useState<boolean>(false);
  const [vivaError, setVivaError] = useState<string | null>(null);
  const [latestVivaResult, setLatestVivaResult] = useState<VivaAnswerResponse | null>(null);

  // Load projects list
  const loadProjects = async () => {
    setLoading(true);
    setBackendOffline(false);
    try {
      const list = await projectApi.getProjects();
      setProjects(list);
      if (list.length > 0 && !selectedProjectId) {
        setSelectedProjectId(list[0].id);
      }
    } catch (err: any) {
      console.warn('Backend appears offline when fetching projects:', err);
      setBackendOffline(true);
    } finally {
      setLoading(false);
    }
  };

  // Load single project details
  const loadProjectDetails = async (id: string) => {
    try {
      const proj = await projectApi.getProject(id);
      setCurrentProject(proj);
      // Load viva questions
      try {
        const vList = await projectApi.getVivaQuestions(id);
        setVivaQuestions(vList);
        if (vList.length > 0 && !selectedVivaId) {
          setSelectedVivaId(vList[0].id);
        }
      } catch (vErr) {
        console.warn('Could not load viva questions:', vErr);
      }
    } catch (err: any) {
      console.warn('Error loading project details:', err);
      setBackendOffline(true);
    }
  };

  // Load available documents for context picker
  const loadDocuments = async () => {
    try {
      const docs = await documentService.getDocuments('ALL');
      setAvailableDocs(docs);
    } catch (err) {
      console.warn('Could not fetch documents:', err);
    }
  };

  useEffect(() => {
    loadProjects();
    loadDocuments();
  }, []);

  useEffect(() => {
    if (selectedProjectId) {
      loadProjectDetails(selectedProjectId);
    }
  }, [selectedProjectId]);

  // Handle Project Creation
  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateSubmitting(true);
    setCreateError(null);

    let parsedConstraints = {};
    try {
      if (createConstraints.trim()) {
        parsedConstraints = JSON.parse(createConstraints);
      }
    } catch (e) {
      setCreateError('Constraints must be valid JSON (e.g. {"budget": "low", "auth": "jwt"})');
      setCreateSubmitting(false);
      return;
    }

    try {
      const newProj = await projectApi.createProject({
        title: createTitle,
        domain: createDomain,
        idea: createIdea,
        constraints: parsedConstraints,
        contextDocumentId: selectedDocId || undefined,
      });

      setProjects((prev) => [newProj, ...prev]);
      setSelectedProjectId(newProj.id);
      setIsCreating(false);
      setCreateTitle('');
      setCreateIdea('');
      setCreateConstraints('{}');
      setSelectedDocId('');
    } catch (err: any) {
      setCreateError(err.response?.data?.message || 'Failed to generate project blueprint.');
    } finally {
      setCreateSubmitting(false);
    }
  };

  // Handle Milestone Toggle
  const handleToggleMilestone = async (milestoneId: string, currentStatus: 'TODO' | 'DOING' | 'DONE') => {
    if (!currentProject) return;
    const nextStatus = currentStatus === 'TODO' ? 'DOING' : currentStatus === 'DOING' ? 'DONE' : 'TODO';

    try {
      await projectApi.updateMilestone(currentProject.id, milestoneId, nextStatus);
      // Update local state
      setCurrentProject((prev) => {
        if (!prev) return null;
        return {
          ...prev,
          milestones: prev.milestones.map((m) =>
            m.id === milestoneId ? { ...m, status: nextStatus } : m
          ),
        };
      });
    } catch (err) {
      console.error('Failed to update milestone:', err);
    }
  };

  // Handle Section Regeneration
  const handleRegenerateSection = async (section: string) => {
    if (!currentProject) return;
    try {
      const updated = await projectApi.regenerateSection(currentProject.id, section);
      setCurrentProject(updated);
    } catch (err) {
      console.error('Failed to regenerate section:', err);
    }
  };

  // Handle Export README
  const handleExportReadme = async () => {
    if (!currentProject) return;
    try {
      const markdown = await projectApi.exportReadme(currentProject.id);
      const blob = new Blob([markdown], { type: 'text/markdown' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${currentProject.title.toLowerCase().replace(/[^a-z0-9]/g, '_')}_README.md`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Failed to export README:', err);
      alert('Export failed. Please check backend status.');
    }
  };

  // Handle Viva Answer Submission
  const handleVivaSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentProject || !selectedVivaId || !vivaAnswerText.trim()) return;

    setVivaSubmitting(true);
    setVivaError(null);
    setLatestVivaResult(null);

    try {
      const result = await projectApi.answerVivaQuestion(
        currentProject.id,
        selectedVivaId,
        vivaAnswerText.trim()
      );
      setLatestVivaResult(result);

      // Update question list previous answers
      setVivaQuestions((prev) =>
        prev.map((q) =>
          q.id === selectedVivaId
            ? { ...q, previousAnswers: [result, ...q.previousAnswers] }
            : q
        )
      );
      setVivaAnswerText('');
    } catch (err: any) {
      setVivaError(err.response?.data?.message || 'Failed to submit answer.');
    } finally {
      setVivaSubmitting(false);
    }
  };

  const selectedQuestion = vivaQuestions.find((q) => q.id === selectedVivaId);

  return (
    <div className="max-w-7xl mx-auto space-y-8 pb-16">
      {/* Backend Offline Warning Banner */}
      {backendOffline && (
        <div className="p-4 bg-amber-950/50 border border-amber-600/50 rounded-xl flex items-center justify-between gap-4 text-amber-200">
          <div className="flex items-center space-x-3">
            <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0" />
            <div>
              <p className="text-sm font-bold">Backend Service Offline</p>
              <p className="text-xs text-amber-300/80">
                Live database and API service (:8080) are unreachable. Start Docker and the Spring Boot backend to generate real-time blueprints.
              </p>
            </div>
          </div>
          <button
            onClick={loadProjects}
            className="flex items-center space-x-1.5 px-3 py-1.5 bg-amber-600/30 hover:bg-amber-600/50 border border-amber-500/40 rounded-lg text-xs font-semibold transition-colors shrink-0"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Retry Connection</span>
          </button>
        </div>
      )}

      {/* Header Bar */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-slate-800 pb-6">
        <div>
          <div className="inline-flex items-center space-x-2 px-3 py-1 bg-indigo-500/10 border border-indigo-500/20 rounded-full text-indigo-400 text-xs font-semibold mb-2">
            <Code className="w-3.5 h-3.5" />
            <span>Pillar 6 • Project Builder & Viva Workbench</span>
          </div>
          <h1 className="text-3xl font-extrabold tracking-tight text-white">
            Project Architecture & Viva Defense
          </h1>
          <p className="text-slate-400 text-sm mt-1">
            Generate full-stack system blueprints, interactive React Flow roadmaps, and viva evaluation rubrics.
          </p>
        </div>

        <div className="flex items-center space-x-3">
          {projects.length > 0 && (
            <select
              value={selectedProjectId || ''}
              onChange={(e) => setSelectedProjectId(e.target.value)}
              className="bg-slate-900 border border-slate-800 rounded-xl px-3.5 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500"
            >
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.title} ({p.domain})
                </option>
              ))}
            </select>
          )}

          <button
            onClick={() => setIsCreating(true)}
            className="flex items-center space-x-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-sm font-semibold shadow-lg shadow-indigo-600/20 transition-all shrink-0"
          >
            <Plus className="w-4 h-4" />
            <span>New Project</span>
          </button>
        </div>
      </div>

      {/* CREATE PROJECT MODAL */}
      {isCreating && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-xl w-full p-6 space-y-5 shadow-2xl">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <h3 className="text-lg font-bold text-white flex items-center gap-2">
                <Sparkles className="w-5 h-5 text-indigo-400" />
                Generate Project Architecture
              </h3>
              <button
                onClick={() => setIsCreating(false)}
                className="text-slate-400 hover:text-white text-sm"
              >
                ✕
              </button>
            </div>

            {createError && (
              <div className="p-3 bg-rose-950/50 border border-rose-800/50 rounded-xl text-xs text-rose-300">
                {createError}
              </div>
            )}

            <form onSubmit={handleCreateSubmit} className="space-y-4 text-sm">
              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">
                  Project Title
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Distributed Video Streaming Platform"
                  value={createTitle}
                  onChange={(e) => setCreateTitle(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-slate-100 focus:outline-none focus:border-indigo-500 text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">
                  Technical Domain
                </label>
                <select
                  value={createDomain}
                  onChange={(e) => setCreateDomain(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-slate-100 focus:outline-none focus:border-indigo-500 text-sm"
                >
                  <option value="Full-stack web">Full-stack web (Spring Boot + React + PostgreSQL)</option>
                  <option value="AI/ML">AI/ML (RAG + pgvector + PyTorch Transformer)</option>
                  <option value="Mobile/Cloud">Mobile/Cloud (React Native + SQLite + Cloud Sync)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">
                  Problem & Idea Description
                </label>
                <textarea
                  required
                  rows={3}
                  placeholder="Explain what the system accomplishes, who uses it, and its main purpose..."
                  value={createIdea}
                  onChange={(e) => setCreateIdea(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-slate-100 focus:outline-none focus:border-indigo-500 text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">
                  Optional Grounding Document (Context Picker)
                </label>
                <select
                  value={selectedDocId}
                  onChange={(e) => setSelectedDocId(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-slate-100 focus:outline-none focus:border-indigo-500 text-sm"
                >
                  <option value="">-- No Document (Use Domain Defaults) --</option>
                  {availableDocs.map((doc) => (
                    <option key={doc.id} value={doc.id}>
                      {doc.fileName} ({doc.subject || 'General'})
                    </option>
                  ))}
                </select>
                <p className="text-[11px] text-slate-500 mt-1">
                  Selected document chunks will be retrieved to ground the architecture proposal.
                </p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">
                  Constraints (JSON)
                </label>
                <input
                  type="text"
                  value={createConstraints}
                  onChange={(e) => setCreateConstraints(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-slate-100 font-mono text-xs focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div className="pt-2 flex justify-end space-x-3">
                <button
                  type="button"
                  onClick={() => setIsCreating(false)}
                  className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-xl text-sm font-semibold transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createSubmitting}
                  className="flex items-center space-x-2 px-5 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-sm font-semibold transition-all disabled:opacity-50"
                >
                  {createSubmitting ? (
                    <>
                      <RefreshCw className="w-4 h-4 animate-spin" />
                      <span>Validating Schema...</span>
                    </>
                  ) : (
                    <>
                      <Sparkles className="w-4 h-4" />
                      <span>Generate Blueprint</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* If No Projects */}
      {!loading && projects.length === 0 && !backendOffline && (
        <div className="text-center py-16 bg-slate-900 border border-slate-800 rounded-2xl p-8 space-y-4">
          <div className="w-16 h-16 mx-auto rounded-2xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
            <Layers className="w-8 h-8" />
          </div>
          <h2 className="text-xl font-bold text-white">No Projects Generated Yet</h2>
          <p className="text-sm text-slate-400 max-w-md mx-auto leading-relaxed">
            Create your first academic or production architecture blueprint. The system validates schemas and generates roadmaps, ER diagrams, and viva evaluations.
          </p>
          <button
            onClick={() => setIsCreating(true)}
            className="inline-flex items-center space-x-2 px-5 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-sm font-semibold shadow-lg shadow-indigo-600/30 transition-all"
          >
            <Plus className="w-4 h-4" />
            <span>Create New Project</span>
          </button>
        </div>
      )}

      {/* Main Project Workspace */}
      {currentProject && (
        <div className="space-y-6">
          {/* Top Project Context Bar */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
            <div className="space-y-1.5">
              <div className="flex items-center gap-2.5">
                <h2 className="text-xl font-extrabold text-white">{currentProject.title}</h2>
                <span className="text-xs px-2.5 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 font-semibold">
                  {currentProject.domain}
                </span>

                {/* Source Badge */}
                {currentProject.source === 'TEMPLATE' ? (
                  <span className="text-[11px] px-2.5 py-0.5 rounded-full bg-slate-800 text-slate-400 border border-slate-700 font-medium">
                    offline template, not AI-generated
                  </span>
                ) : (
                  <span className="text-[11px] px-2.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-medium flex items-center gap-1">
                    <Sparkles className="w-3 h-3" />
                    AI Generated (LLM)
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-400">
                Blueprint Version: <strong className="text-slate-200">v{currentProject.blueprintVersion || 1}</strong> • Created:{' '}
                {new Date(currentProject.createdAt).toLocaleDateString()}
              </p>
            </div>

            <div className="flex items-center space-x-3">
              <button
                onClick={handleExportReadme}
                className="flex items-center space-x-1.5 px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs font-semibold border border-slate-700 transition-colors"
              >
                <Download className="w-3.5 h-3.5 text-indigo-400" />
                <span>Export README</span>
              </button>
            </div>
          </div>

          {/* Tab Navigation */}
          <div className="flex space-x-2 border-b border-slate-800 overflow-x-auto pb-2">
            {[
              { id: 'overview', label: 'Overview' },
              { id: 'features', label: 'Features' },
              { id: 'stack', label: 'Tech Stack' },
              { id: 'er', label: 'ER Diagram' },
              { id: 'api', label: 'API Specs' },
              { id: 'architecture', label: 'Architecture' },
              { id: 'roadmap', label: 'Roadmap & Graph' },
              { id: 'files', label: 'File Tree' },
              { id: 'viva', label: 'Viva Defense' },
            ].map((tab) => {
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id as any)}
                  className={`px-4 py-2 rounded-xl text-xs font-semibold transition-all shrink-0 ${
                    isActive
                      ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/30'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
                  }`}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>

          {/* TAB 1: OVERVIEW */}
          {activeTab === 'overview' && (
            <div className="space-y-6">
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <BookOpen className="w-4 h-4 text-indigo-400" />
                  Problem Statement & Solution Scope
                </h3>
                <p className="text-sm text-slate-300 leading-relaxed bg-slate-950 p-4 rounded-xl border border-slate-800">
                  {currentProject.blueprintContent.problemStatement || currentProject.idea}
                </p>
              </div>

              {/* Risks & Mitigation */}
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-amber-400" />
                  Architectural Risks & Tradeoffs
                </h3>
                <div className="space-y-2">
                  {(currentProject.blueprintContent.risks || []).map((risk, idx) => (
                    <div
                      key={idx}
                      className="p-3 bg-slate-950 rounded-xl text-xs text-slate-300 border border-slate-800 flex items-start space-x-3"
                    >
                      <span className="w-5 h-5 rounded-full bg-amber-500/10 text-amber-400 flex items-center justify-center text-[10px] font-bold shrink-0">
                        {idx + 1}
                      </span>
                      <p className="mt-0.5 leading-relaxed">{risk}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: FEATURES */}
          {activeTab === 'features' && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                  <h3 className="text-base font-bold text-emerald-400 flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4" />
                    MVP Deliverables
                  </h3>
                  <span className="text-xs text-slate-400">Core Scope</span>
                </div>
                <div className="space-y-2.5">
                  {(currentProject.blueprintContent.features?.mvp || []).map((feat, idx) => (
                    <div
                      key={idx}
                      className="p-3 bg-slate-950 rounded-xl text-xs text-slate-200 border border-slate-800 flex items-center space-x-3"
                    >
                      <Check className="w-4 h-4 text-emerald-400 shrink-0" />
                      <span>{feat}</span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                  <h3 className="text-base font-bold text-indigo-400 flex items-center gap-2">
                    <Zap className="w-4 h-4" />
                    Future Phase Features
                  </h3>
                  <span className="text-xs text-slate-400">Roadmap</span>
                </div>
                <div className="space-y-2.5">
                  {(currentProject.blueprintContent.features?.later || []).map((feat, idx) => (
                    <div
                      key={idx}
                      className="p-3 bg-slate-950 rounded-xl text-xs text-slate-300 border border-slate-800 flex items-center space-x-3"
                    >
                      <ArrowRight className="w-4 h-4 text-indigo-400 shrink-0" />
                      <span>{feat}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: TECH STACK */}
          {activeTab === 'stack' && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden">
              <div className="p-5 border-b border-slate-800">
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <Sliders className="w-4 h-4 text-indigo-400" />
                  Technology Stack Decisions
                </h3>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs text-slate-300">
                  <thead className="bg-slate-950/80 uppercase font-semibold text-slate-400 border-b border-slate-800">
                    <tr>
                      <th className="py-3 px-5">Layer</th>
                      <th className="py-3 px-5">Choice</th>
                      <th className="py-3 px-5">Architectural Rationale</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-sans">
                    {(currentProject.blueprintContent.techStack || []).map((s, idx) => (
                      <tr key={idx} className="hover:bg-slate-800/30">
                        <td className="py-3.5 px-5 font-semibold text-slate-200">{s.layer}</td>
                        <td className="py-3.5 px-5 font-mono text-indigo-400 font-semibold">{s.choice}</td>
                        <td className="py-3.5 px-5 text-slate-400 leading-relaxed">{s.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* TAB 4: ER DIAGRAM */}
          {activeTab === 'er' && (
            <div className="space-y-6">
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                  <h3 className="text-base font-bold text-white flex items-center gap-2">
                    <Database className="w-4 h-4 text-purple-400" />
                    Entity-Relationship Schema (Strict Mermaid)
                  </h3>
                  <button
                    onClick={() => handleRegenerateSection('erDiagram')}
                    className="flex items-center space-x-1 px-3 py-1 bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-200 rounded-lg transition-colors border border-slate-700"
                  >
                    <RefreshCw className="w-3.5 h-3.5" />
                    <span>Regenerate Diagram</span>
                  </button>
                </div>

                <StrictMermaid
                  chartText={currentProject.blueprintContent.erDiagram || 'erDiagram\n  USER ||--o{ PROJECT : owns'}
                  sectionName="erDiagram"
                  onRegenerate={handleRegenerateSection}
                />
              </div>

              {/* Entities List */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {(currentProject.blueprintContent.entities || []).map((ent, idx) => (
                  <div key={idx} className="bg-slate-900 border border-slate-800 rounded-xl p-4 space-y-2">
                    <h4 className="font-bold text-sm text-indigo-400 font-mono">{ent.name}</h4>
                    <div className="space-y-1">
                      {ent.attributes.map((attr, aIdx) => (
                        <p key={aIdx} className="text-xs text-slate-400 font-mono bg-slate-950 px-2.5 py-1 rounded">
                          {attr}
                        </p>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TAB 5: API SPECIFICATIONS */}
          {activeTab === 'api' && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden">
              <div className="p-5 border-b border-slate-800">
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <Terminal className="w-4 h-4 text-emerald-400" />
                  REST API Contract
                </h3>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs text-slate-300">
                  <thead className="bg-slate-950/80 uppercase font-semibold text-slate-400 border-b border-slate-800">
                    <tr>
                      <th className="py-3 px-5">Method</th>
                      <th className="py-3 px-5">Endpoint Path</th>
                      <th className="py-3 px-5">Purpose & Function</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-sans">
                    {(currentProject.blueprintContent.apiEndpoints || []).map((ep, idx) => (
                      <tr key={idx} className="hover:bg-slate-800/30">
                        <td className="py-3.5 px-5 font-bold">
                          <span
                            className={`px-2 py-0.5 rounded text-[11px] font-mono ${
                              ep.method === 'GET'
                                ? 'bg-blue-500/10 text-blue-400 border border-blue-500/20'
                                : ep.method === 'POST'
                                ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                                : ep.method === 'PATCH'
                                ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                                : 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                            }`}
                          >
                            {ep.method}
                          </span>
                        </td>
                        <td className="py-3.5 px-5 font-mono text-slate-200 font-semibold">{ep.path}</td>
                        <td className="py-3.5 px-5 text-slate-400">{ep.purpose}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* TAB 6: ARCHITECTURE TOPOLOGY */}
          {activeTab === 'architecture' && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <Layers className="w-4 h-4 text-indigo-400" />
                  System Topology Diagram (Strict Mermaid)
                </h3>
                <button
                  onClick={() => handleRegenerateSection('architectureDiagram')}
                  className="flex items-center space-x-1 px-3 py-1 bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-200 rounded-lg transition-colors border border-slate-700"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                  <span>Regenerate Diagram</span>
                </button>
              </div>

              <StrictMermaid
                chartText={currentProject.blueprintContent.architectureDiagram || 'graph TD;\n  Client-->Server;'}
                sectionName="architectureDiagram"
                onRegenerate={handleRegenerateSection}
              />
            </div>
          )}

          {/* TAB 7: ROADMAP & REACT FLOW GRAPH */}
          {activeTab === 'roadmap' && (
            <div className="space-y-6">
              <RoadmapGraph
                phases={currentProject.blueprintContent.roadmap?.phases || []}
                milestones={currentProject.milestones}
                onToggleMilestone={handleToggleMilestone}
              />
            </div>
          )}

          {/* TAB 8: FILES */}
          {activeTab === 'files' && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden">
              <div className="p-5 border-b border-slate-800">
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <FileText className="w-4 h-4 text-indigo-400" />
                  Project Structure & File Organization
                </h3>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs text-slate-300">
                  <thead className="bg-slate-950/80 uppercase font-semibold text-slate-400 border-b border-slate-800">
                    <tr>
                      <th className="py-3 px-5">Relative File Path</th>
                      <th className="py-3 px-5">Responsibility & Purpose</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-sans">
                    {(currentProject.blueprintContent.fileTree || []).map((f, idx) => (
                      <tr key={idx} className="hover:bg-slate-800/30">
                        <td className="py-3.5 px-5 font-mono text-indigo-400 font-semibold">{f.path}</td>
                        <td className="py-3.5 px-5 text-slate-400">{f.purpose}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* TAB 9: VIVA PRACTICE & EVALUATOR */}
          {activeTab === 'viva' && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
              {/* Question Selection Sidebar */}
              <div className="lg:col-span-5 bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-3 max-h-[600px] overflow-y-auto">
                <h3 className="text-sm font-bold text-white flex items-center gap-2 border-b border-slate-800 pb-2">
                  <HelpCircle className="w-4 h-4 text-indigo-400" />
                  Viva Questions ({vivaQuestions.length})
                </h3>

                <div className="space-y-2">
                  {vivaQuestions.map((q, idx) => {
                    const isSelected = selectedVivaId === q.id;
                    const hasAnswered = q.previousAnswers && q.previousAnswers.length > 0;
                    return (
                      <div
                        key={q.id}
                        onClick={() => setSelectedVivaId(q.id)}
                        className={`p-3 rounded-xl border cursor-pointer transition-all select-none ${
                          isSelected
                            ? 'bg-indigo-950/40 border-indigo-600 text-white shadow-md'
                            : 'bg-slate-950 border-slate-800/80 text-slate-300 hover:border-slate-700'
                        }`}
                      >
                        <div className="flex items-center justify-between text-[10px] text-slate-400 mb-1">
                          <span>Question 0{idx + 1}</span>
                          {hasAnswered && (
                            <span className="text-emerald-400 font-semibold flex items-center gap-1">
                              <CheckCircle2 className="w-3 h-3" />
                              Evaluated
                            </span>
                          )}
                        </div>
                        <p className="text-xs font-semibold leading-snug">{q.question}</p>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Evaluation Workbench */}
              <div className="lg:col-span-7 space-y-6">
                {selectedQuestion ? (
                  <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-5">
                    <div>
                      <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                        {selectedQuestion.topic || 'Architecture Defense'}
                      </span>
                      <h3 className="text-base font-bold text-white mt-2 leading-relaxed">
                        {selectedQuestion.question}
                      </h3>
                    </div>

                    {/* Model Answer Preview (Collapsible) */}
                    <details className="bg-slate-950 border border-slate-800 rounded-xl p-3.5 group">
                      <summary className="text-xs font-bold text-indigo-400 cursor-pointer select-none">
                        Show Expert Model Answer
                      </summary>
                      <p className="text-xs text-slate-300 mt-2 leading-relaxed border-t border-slate-800/60 pt-2">
                        {selectedQuestion.modelAnswer}
                      </p>
                    </details>

                    {/* Answer Input Form */}
                    <form onSubmit={handleVivaSubmit} className="space-y-3">
                      <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider">
                        Your Viva Answer (Server-Graded Rubric)
                      </label>
                      <textarea
                        rows={4}
                        required
                        placeholder="Type your explanation clearly addressing the technical concepts..."
                        value={vivaAnswerText}
                        onChange={(e) => setVivaAnswerText(e.target.value)}
                        className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-sm text-slate-100 focus:outline-none focus:border-indigo-500 leading-relaxed"
                      />

                      {vivaError && (
                        <p className="text-xs text-rose-400 font-semibold">{vivaError}</p>
                      )}

                      <div className="flex justify-end">
                        <button
                          type="submit"
                          disabled={vivaSubmitting || !vivaAnswerText.trim()}
                          className="flex items-center space-x-2 px-5 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-semibold shadow-lg shadow-indigo-600/20 transition-all disabled:opacity-50"
                        >
                          {vivaSubmitting ? (
                            <>
                              <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                              <span>Judging Answer...</span>
                            </>
                          ) : (
                            <>
                              <Send className="w-3.5 h-3.5" />
                              <span>Submit for Evaluation</span>
                            </>
                          )}
                        </button>
                      </div>
                    </form>

                    {/* Evaluation Result Feedback Card */}
                    {(latestVivaResult || (selectedQuestion.previousAnswers && selectedQuestion.previousAnswers[0])) && (
                      <div className="p-4 bg-slate-950 border border-indigo-900/50 rounded-xl space-y-3">
                        {(() => {
                          const res = latestVivaResult || selectedQuestion.previousAnswers[0];
                          return (
                            <>
                              <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                                <span className="text-xs font-bold text-white flex items-center gap-1.5">
                                  <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
                                  Server Evaluation Result
                                </span>
                                <span className="text-[10px] px-2 py-0.5 rounded font-bold uppercase bg-slate-800 text-slate-400 border border-slate-700">
                                  Judged by: {res.judgedBy}
                                </span>
                              </div>

                              {/* Scores Grid */}
                              <div className="grid grid-cols-4 gap-2 text-center">
                                <div className="p-2 bg-slate-900 rounded-lg border border-slate-800">
                                  <p className="text-[10px] text-slate-400 uppercase">Correctness</p>
                                  <p className="text-base font-extrabold text-indigo-400 mt-0.5">
                                    {res.scores.correctness}/10
                                  </p>
                                </div>
                                <div className="p-2 bg-slate-900 rounded-lg border border-slate-800">
                                  <p className="text-[10px] text-slate-400 uppercase">Completeness</p>
                                  <p className="text-base font-extrabold text-purple-400 mt-0.5">
                                    {res.scores.completeness}/10
                                  </p>
                                </div>
                                <div className="p-2 bg-slate-900 rounded-lg border border-slate-800">
                                  <p className="text-[10px] text-slate-400 uppercase">Clarity</p>
                                  <p className="text-base font-extrabold text-emerald-400 mt-0.5">
                                    {res.scores.clarity}/10
                                  </p>
                                </div>
                                <div className="p-2 bg-slate-900 rounded-lg border border-slate-800">
                                  <p className="text-[10px] text-slate-400 uppercase">Overall</p>
                                  <p className="text-base font-extrabold text-amber-400 mt-0.5">
                                    {res.scores.overall}/10
                                  </p>
                                </div>
                              </div>

                              <div>
                                <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                                  Feedback & Justification:
                                </p>
                                <p className="text-xs text-slate-300 leading-relaxed bg-slate-900/60 p-3 rounded-lg border border-slate-800/80">
                                  {res.feedback}
                                </p>
                              </div>
                            </>
                          );
                        })()}
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="p-8 text-center text-slate-400 bg-slate-900 rounded-2xl border border-slate-800 text-sm">
                    Select a viva question on the left to review or submit your answer.
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default ProjectBuilderPage;
