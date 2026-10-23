import React, { useMemo } from 'react';
import { ReactFlow, Background, Controls, Node, Edge } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Check, CheckCircle2, Clock } from 'lucide-react';

interface MilestoneItem {
  id: string;
  phase: string;
  title: string;
  description: string;
  status: 'TODO' | 'DOING' | 'DONE';
  position: number;
}

interface RoadmapGraphProps {
  phases: Array<{
    name: string;
    milestones: Array<{
      title: string;
      description: string;
    }>;
  }>;
  milestones: MilestoneItem[];
  onToggleMilestone: (milestoneId: string, currentStatus: 'TODO' | 'DOING' | 'DONE') => void;
}

export const RoadmapGraph: React.FC<RoadmapGraphProps> = ({
  phases,
  milestones,
  onToggleMilestone,
}) => {
  // Construct nodes from phases
  const nodes: Node[] = useMemo(() => {
    if (!phases || phases.length === 0) return [];
    return phases.map((phase, index) => {
      const phaseMilestones = milestones.filter((m) => m.phase === phase.name);
      const doneCount = phaseMilestones.filter((m) => m.status === 'DONE').length;
      const totalCount = phaseMilestones.length;
      const isComplete = totalCount > 0 && doneCount === totalCount;

      return {
        id: `phase-${index}`,
        position: { x: 40, y: index * 130 + 30 },
        data: {
          label: (
            <div className="p-3 text-left">
              <div className="flex items-center justify-between gap-2 mb-1">
                <span className="text-[10px] font-bold text-indigo-400 uppercase tracking-wider">
                  Phase 0{index + 1}
                </span>
                <span
                  className={`text-[10px] px-2 py-0.5 rounded-full font-semibold ${
                    isComplete
                      ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                      : 'bg-indigo-500/20 text-indigo-300 border border-indigo-500/30'
                  }`}
                >
                  {doneCount}/{totalCount} Done
                </span>
              </div>
              <p className="text-xs font-bold text-white leading-tight">{phase.name}</p>
            </div>
          ),
        },
        style: {
          background: '#0f172a',
          border: isComplete ? '1px solid #10b981' : '1px solid #334155',
          borderRadius: '12px',
          width: 260,
          color: '#f8fafc',
          boxShadow: '0 4px 12px rgba(0,0,0,0.5)',
        },
      };
    });
  }, [phases, milestones]);

  // Construct edges connecting sequential phases
  const edges: Edge[] = useMemo(() => {
    if (!phases || phases.length <= 1) return [];
    const edgeList: Edge[] = [];
    for (let i = 0; i < phases.length - 1; i++) {
      edgeList.push({
        id: `e-phase-${i}-${i + 1}`,
        source: `phase-${i}`,
        target: `phase-${i + 1}`,
        animated: true,
        style: { stroke: '#6366f1', strokeWidth: 2 },
      });
    }
    return edgeList;
  }, [phases]);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
      {/* React Flow Visual Canvas */}
      <div className="lg:col-span-6 bg-slate-950 border border-slate-800 rounded-2xl h-[480px] overflow-hidden relative shadow-inner">
        <div className="absolute top-3 left-4 z-10 bg-slate-900/90 border border-slate-800 px-3 py-1 rounded-lg text-xs font-semibold text-slate-300">
          Architecture Roadmap Graph
        </div>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          fitView
          fitViewOptions={{ padding: 0.3 }}
          attributionPosition="bottom-left"
          proOptions={{ hideAttribution: true }}
        >
          <Background color="#334155" gap={16} />
          <Controls className="!bg-slate-900 !border-slate-800 !fill-slate-300 !text-slate-300" />
        </ReactFlow>
      </div>

      {/* Milestone Checklist Beside It */}
      <div className="lg:col-span-6 bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4 max-h-[480px] overflow-y-auto">
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center space-x-2">
            <CheckCircle2 className="w-5 h-5 text-indigo-400" />
            <h3 className="text-base font-bold text-white">Execution Milestones</h3>
          </div>
          <span className="text-xs text-slate-400">
            {milestones.filter((m) => m.status === 'DONE').length} of {milestones.length} Completed
          </span>
        </div>

        <div className="space-y-3">
          {milestones.map((m) => {
            const isDone = m.status === 'DONE';
            const isDoing = m.status === 'DOING';

            return (
              <div
                key={m.id}
                onClick={() => onToggleMilestone(m.id, m.status)}
                className={`p-3.5 rounded-xl border transition-all cursor-pointer flex items-start space-x-3.5 select-none ${
                  isDone
                    ? 'bg-slate-950/40 border-emerald-900/40 text-slate-400'
                    : isDoing
                    ? 'bg-indigo-950/30 border-indigo-700/50 text-slate-100'
                    : 'bg-slate-950/80 border-slate-800 hover:border-slate-700 text-slate-200'
                }`}
              >
                <div
                  className={`mt-0.5 w-5 h-5 rounded-md border flex items-center justify-center shrink-0 transition-colors ${
                    isDone
                      ? 'bg-emerald-600 border-emerald-500 text-white'
                      : isDoing
                      ? 'bg-indigo-600 border-indigo-500 text-white'
                      : 'border-slate-600 bg-slate-900'
                  }`}
                >
                  {isDone && <Check className="w-3.5 h-3.5 stroke-[3]" />}
                  {isDoing && <Clock className="w-3 h-3 text-white" />}
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-2">
                    <p
                      className={`text-sm font-semibold leading-tight ${
                        isDone ? 'line-through text-slate-500' : 'text-slate-100'
                      }`}
                    >
                      {m.title}
                    </p>
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded uppercase tracking-wider shrink-0 ${
                        isDone
                          ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                          : isDoing
                          ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                          : 'bg-slate-800 text-slate-400 border border-slate-700'
                      }`}
                    >
                      {m.status}
                    </span>
                  </div>
                  {m.description && (
                    <p className="text-xs text-slate-400 mt-1 leading-snug">
                      {m.description}
                    </p>
                  )}
                  <p className="text-[11px] text-indigo-400/80 mt-1.5 font-medium">
                    {m.phase}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
