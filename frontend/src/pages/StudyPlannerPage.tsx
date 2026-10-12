import React, { useState, useEffect, useMemo, useRef } from 'react';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import interactionPlugin from '@fullcalendar/interaction';
import { EventClickArg } from '@fullcalendar/core';
import { fsrs, Rating, createEmptyCard, RecordLogItem, Grade } from 'ts-fsrs';
import {
  Calendar as CalendarIcon,
  Clock,
  Plus,
  CheckCircle2,
  RotateCcw,
  Sparkles,
  Layers,
  Play,
  Pause,
  ChevronRight,
  X,
  Target,
  Brain,
  Check
} from 'lucide-react';
import {
  fetchStudyPlans,
  createStudyPlan,
  previewStudyPlan,
  updateStudyTaskStatus,
  triggerCatchUp,
  injectWeakTopics,
  startPomodoroSession,
  completePomodoroSession
} from '../api/study';
import {
  StudyPlanDto,
  StudyTaskDto,
  ScheduleResult,
  FlashcardDeck
} from '../types/study';

// Pre-seeded high yield CS flashcard decks
const INITIAL_DECKS: FlashcardDeck[] = [
  {
    id: 'cs-os-concurrency',
    title: 'OS & Concurrency',
    description: 'Processes, threads, semaphores, and memory virtualisation',
    topic: 'Operating Systems',
    cardCount: 4,
    cards: [
      {
        id: 'c1',
        deckId: 'cs-os-concurrency',
        front: 'What are the 4 Coffman conditions required for a deadlock?',
        back: '1. Mutual Exclusion\n2. Hold and Wait\n3. No Preemption\n4. Circular Wait',
        topic: 'Deadlocks',
        cardState: createEmptyCard(),
        historyCount: 0
      },
      {
        id: 'c2',
        deckId: 'cs-os-concurrency',
        front: 'Difference between mutex and binary semaphore?',
        back: 'A Mutex has ownership (only the locking thread can unlock). A Semaphore is a signaling mechanism (any thread can signal/release).',
        topic: 'Concurrency',
        cardState: createEmptyCard(),
        historyCount: 0
      },
      {
        id: 'c3',
        deckId: 'cs-os-concurrency',
        front: 'Explain the TLB (Translation Lookaside Buffer) reach and hit rate.',
        back: 'TLB is a hardware cache of virtual-to-physical address mappings. TLB reach = TLB entries × Page Size. Hit misses incur full page table walks.',
        topic: 'Virtual Memory',
        cardState: createEmptyCard(),
        historyCount: 0
      },
      {
        id: 'c4',
        deckId: 'cs-os-concurrency',
        front: 'What is Copy-on-Write (CoW) during fork()?',
        back: 'fork() shares physical pages as read-only between parent and child. A page is only duplicated when either process attempts a write.',
        topic: 'Process Management',
        cardState: createEmptyCard(),
        historyCount: 0
      }
    ]
  },
  {
    id: 'cs-dbms-sql',
    title: 'DBMS & ACID Transactions',
    description: 'Isolation levels, write-ahead logs, and B+ Tree indexing',
    topic: 'Database Systems',
    cardCount: 3,
    cards: [
      {
        id: 'c5',
        deckId: 'cs-dbms-sql',
        front: 'List the 4 ANSI SQL isolation levels and phenomena they allow.',
        back: '1. Read Uncommitted: Dirty reads\n2. Read Committed: Non-repeatable reads\n3. Repeatable Read: Phantom reads\n4. Serializable: Strict serial execution',
        topic: 'Transactions',
        cardState: createEmptyCard(),
        historyCount: 0
      },
      {
        id: 'c6',
        deckId: 'cs-dbms-sql',
        front: 'Why are B+ Trees preferred over B Trees for disk-based relational storage?',
        back: 'All data resides in leaf nodes (forming a linked list for range queries). Internal nodes hold only keys, yielding higher branching factor and fewer disk I/Os.',
        topic: 'Indexing',
        cardState: createEmptyCard(),
        historyCount: 0
      },
      {
        id: 'c7',
        deckId: 'cs-dbms-sql',
        front: 'What is WAL (Write-Ahead Logging) and the ARIES recovery algorithm?',
        back: 'Changes are written sequentially to persistent append-only logs before dirty buffer pages hit disk. ARIES uses Analysis, Redo, and Undo passes for crash recovery.',
        topic: 'Durability',
        cardState: createEmptyCard(),
        historyCount: 0
      }
    ]
  }
];

export const StudyPlannerPage: React.FC = () => {
  // Navigation tabs
  const [activeTab, setActiveTab] = useState<'calendar' | 'flashcards' | 'pomodoro'>('calendar');

  // Study plans state
  const [plans, setPlans] = useState<StudyPlanDto[]>([]);
  const [selectedPlan, setSelectedPlan] = useState<StudyPlanDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Modals
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [selectedTask, setSelectedTask] = useState<StudyTaskDto | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  // Form State for Plan Creator
  const [title, setTitle] = useState('');
  const [examDate, setExamDate] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() + 30);
    return d.toISOString().split('T')[0];
  });
  const [hoursPerDay, setHoursPerDay] = useState(2.5);
  const [syllabusText, setSyllabusText] = useState(
    '1. Operating Systems: Concurrency, Deadlocks, Memory Management\n2. Computer Networks: TCP/IP, DNS, Routing Algorithms\n3. Database Management: SQL, Normalization, ACID Transactions\n4. Data Structures & Algorithms: Graphs, Dynamic Programming, Heaps'
  );
  const [previewResult, setPreviewResult] = useState<ScheduleResult | null>(null);
  const [isPreviewing, setIsPreviewing] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // FSRS Flashcard State
  const [decks, setDecks] = useState<FlashcardDeck[]>(() => {
    const saved = localStorage.getItem('genz_flashcard_decks');
    return saved ? JSON.parse(saved) : INITIAL_DECKS;
  });
  const [activeDeckId, setActiveDeckId] = useState<string>(INITIAL_DECKS[0].id);
  const [activeCardIndex, setActiveCardIndex] = useState(0);
  const [isFlipped, setIsFlipped] = useState(false);
  const fsrsScheduler = useMemo(() => fsrs(), []);

  // Pomodoro Focus Timer State
  const [pomodoroSeconds, setPomodoroSeconds] = useState(25 * 60);
  const [isTimerRunning, setIsTimerRunning] = useState(false);
  const [currentPomodoroSessionId, setCurrentPomodoroSessionId] = useState<string | null>(null);
  const timerRef = useRef<number | null>(null);

  // Load study plans on mount
  useEffect(() => {
    loadPlans();
  }, []);

  // Save flashcards to localStorage on change
  useEffect(() => {
    localStorage.setItem('genz_flashcard_decks', JSON.stringify(decks));
  }, [decks]);

  const loadPlans = async () => {
    try {
      setIsLoading(true);
      setError(null);
      const data = await fetchStudyPlans();
      setPlans(data);
      if (data.length > 0) {
        setSelectedPlan(data[0]);
      }
    } catch (err: any) {
      console.error('Failed to load study plans:', err);
      setError(err?.response?.data?.message || 'Could not connect to study planner backend.');
    } finally {
      setIsLoading(false);
    }
  };

  // Convert tasks in selected plan to FullCalendar event format
  const calendarEvents = useMemo(() => {
    if (!selectedPlan || !selectedPlan.tasksByDate) return [];

    const events: any[] = [];
    Object.entries(selectedPlan.tasksByDate).forEach(([dateStr, tasks]) => {
      tasks.forEach((t) => {
        let bgColor = '#3b82f6'; // default blue
        let borderColor = '#2563eb';
        let textColor = '#ffffff';

        if (t.status === 'DONE') {
          bgColor = '#10b981'; // emerald
          borderColor = '#059669';
        } else if (t.status === 'SKIPPED') {
          bgColor = '#64748b'; // slate
          borderColor = '#475569';
        } else if (t.type === 'REVISION' || t.type === 'APTITUDE_REVISION') {
          bgColor = '#8b5cf6'; // purple
          borderColor = '#7c3aed';
        } else if (t.type === 'BUFFER') {
          bgColor = '#f59e0b'; // amber
          borderColor = '#d97706';
        }

        events.push({
          id: t.id,
          title: `${t.type === 'REVISION' ? '🔄 ' : ''}${t.topic} (${t.plannedMinutes}m)`,
          start: dateStr,
          allDay: true,
          backgroundColor: bgColor,
          borderColor: borderColor,
          textColor: textColor,
          extendedProps: {
            task: t,
            planId: selectedPlan.id
          }
        });
      });
    });

    // Also mark the exam date on the calendar
    if (selectedPlan.examDate) {
      events.push({
        id: 'exam-target-milestone',
        title: `🎯 EXAM DAY: ${selectedPlan.title}`,
        start: selectedPlan.examDate,
        allDay: true,
        backgroundColor: '#ef4444',
        borderColor: '#b91c1c',
        textColor: '#ffffff'
      });
    }

    return events;
  }, [selectedPlan]);

  const handleEventClick = (info: EventClickArg) => {
    if (info.event.id === 'exam-target-milestone') return;
    const task = info.event.extendedProps.task as StudyTaskDto;
    setSelectedTask(task);
  };

  const handleUpdateTaskStatus = async (taskId: string, newStatus: 'DONE' | 'SKIPPED' | 'PENDING') => {
    if (!selectedPlan) return;
    try {
      const updated = await updateStudyTaskStatus(selectedPlan.id, taskId, { status: newStatus });
      // Update local state
      const updatedTasksByDate = { ...selectedPlan.tasksByDate };
      Object.keys(updatedTasksByDate).forEach((d) => {
        updatedTasksByDate[d] = updatedTasksByDate[d].map((t) =>
          t.id === taskId ? { ...t, status: updated.status } : t
        );
      });

      setSelectedPlan({
        ...selectedPlan,
        tasksByDate: updatedTasksByDate
      });
      setSelectedTask(null);
      showMessage(`Task marked as ${newStatus}`);
    } catch (err: any) {
      console.error('Failed to update task:', err);
      showMessage('Failed to update task status.');
    }
  };

  const handleRunCatchUp = async () => {
    if (!selectedPlan) return;
    try {
      const res = await triggerCatchUp(selectedPlan.id);
      showMessage(res.message || `Catch-up complete: ${res.rescheduledTasksCount} tasks rescheduled.`);
      loadPlans();
    } catch (err: any) {
      showMessage(err?.response?.data?.message || 'Error running catch-up.');
    }
  };

  const handleInjectWeakTopics = async () => {
    if (!selectedPlan) return;
    try {
      const res = await injectWeakTopics(selectedPlan.id);
      showMessage(`Injected ${res.addedTasksCount} targeted aptitude revision tasks into your schedule!`);
      loadPlans();
    } catch (err: any) {
      showMessage(err?.response?.data?.message || 'Error injecting weak topic tasks.');
    }
  };

  const handlePreviewPlan = async () => {
    try {
      setIsPreviewing(true);
      const res = await previewStudyPlan({
        examDate,
        hoursPerDay,
        syllabusText
      });
      setPreviewResult(res);
    } catch (err: any) {
      showMessage(err?.response?.data?.message || 'Preview generation failed.');
    } finally {
      setIsPreviewing(false);
    }
  };

  const handleCreatePlan = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      showMessage('Please provide a title for your study plan.');
      return;
    }

    try {
      setIsSubmitting(true);
      const newPlan = await createStudyPlan({
        title,
        examDate,
        hoursPerDay,
        syllabusText
      });
      setShowCreateModal(false);
      showMessage(`Created study plan "${newPlan.title}" successfully!`);
      await loadPlans();
      setSelectedPlan(newPlan);
    } catch (err: any) {
      showMessage(err?.response?.data?.message || 'Failed to create plan.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const showMessage = (msg: string) => {
    setActionMessage(msg);
    setTimeout(() => setActionMessage(null), 4000);
  };

  // FSRS Flashcard Rating Logic
  const currentDeck = useMemo(
    () => decks.find((d) => d.id === activeDeckId) || decks[0],
    [decks, activeDeckId]
  );
  const currentCard = currentDeck?.cards[activeCardIndex];

  // Calculate upcoming intervals for Again, Hard, Good, Easy via ts-fsrs
  const fsrsPreviewSchedule = useMemo(() => {
    if (!currentCard) return null;
    const now = new Date();
    try {
      const repeatRecord = fsrsScheduler.repeat(currentCard.cardState, now);
      return {
        again: repeatRecord[Rating.Again as Grade],
        hard: repeatRecord[Rating.Hard as Grade],
        good: repeatRecord[Rating.Good as Grade],
        easy: repeatRecord[Rating.Easy as Grade]
      };
    } catch (e) {
      return null;
    }
  }, [currentCard, fsrsScheduler]);

  const handleRateCard = (rating: Rating) => {
    if (!currentCard) return;
    const now = new Date();
    const schedulingRecord = fsrsScheduler.repeat(currentCard.cardState, now);
    const resultItem: RecordLogItem = schedulingRecord[rating as Grade];

    // Update the card state with the newly computed FSRS parameters
    const updatedCards = currentDeck.cards.map((c) =>
      c.id === currentCard.id
        ? {
            ...c,
            cardState: resultItem.card,
            historyCount: c.historyCount + 1
          }
        : c
    );

    setDecks(
      decks.map((d) =>
        d.id === currentDeck.id
          ? {
              ...d,
              cards: updatedCards
            }
          : d
      )
    );

    setIsFlipped(false);
    // Advance to next card
    if (activeCardIndex < currentDeck.cards.length - 1) {
      setActiveCardIndex((prev) => prev + 1);
    } else {
      setActiveCardIndex(0);
      showMessage('🎉 You completed a full review cycle for this deck!');
    }
  };

  // Pomodoro timer ticker
  useEffect(() => {
    if (isTimerRunning) {
      timerRef.current = window.setInterval(() => {
        setPomodoroSeconds((prev) => {
          if (prev <= 1) {
            handleCompletePomodoro();
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } else if (timerRef.current) {
      clearInterval(timerRef.current);
    }

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [isTimerRunning]);

  const handleStartPomodoro = async (task?: StudyTaskDto) => {
    try {
      const session = await startPomodoroSession({
        taskId: task?.id,
        plannedSeconds: 1500
      });
      setCurrentPomodoroSessionId(session.id);
      setPomodoroSeconds(25 * 60);
      setIsTimerRunning(true);
      setActiveTab('pomodoro');
      if (task) setSelectedTask(task);
      showMessage('🍅 25-minute Pomodoro focus session started!');
    } catch (err) {
      // Fallback local timer
      setPomodoroSeconds(25 * 60);
      setIsTimerRunning(true);
      setActiveTab('pomodoro');
    }
  };

  const handleCompletePomodoro = async () => {
    setIsTimerRunning(false);
    if (currentPomodoroSessionId) {
      try {
        await completePomodoroSession(currentPomodoroSessionId);
      } catch (err) {
        console.error('Error logging pomodoro completion:', err);
      }
    }
    showMessage('🌟 Excellent! Pomodoro focus session finished and recorded.');
  };

  const formatTimer = (totalSec: number) => {
    const mins = Math.floor(totalSec / 60);
    const secs = totalSec % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const formatIntervalDays = (due: Date) => {
    const now = new Date();
    const diffMs = new Date(due).getTime() - now.getTime();
    const diffHours = Math.round(diffMs / (1000 * 60 * 60));
    if (diffHours < 24) return `< 1d`;
    const days = Math.round(diffHours / 24);
    return `${days}d`;
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 text-slate-100 overflow-y-auto">
      {/* Top Header */}
      <div className="border-b border-slate-800 bg-slate-900/60 backdrop-blur px-8 py-5 flex flex-wrap items-center justify-between gap-4 sticky top-0 z-10">
        <div>
          <div className="flex items-center space-x-3">
            <div className="p-2.5 rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-600 text-white shadow-lg shadow-indigo-500/20">
              <CalendarIcon className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-xl font-bold tracking-tight text-white flex items-center gap-2">
                Study Planner & Spaced Repetition
                <span className="text-xs px-2.5 py-0.5 rounded-full font-semibold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                  FSRS v6 + FullCalendar
                </span>
              </h1>
              <p className="text-xs text-slate-400">
                Automated syllabus pacing, exam milestone tracking, and optimal memory retention
              </p>
            </div>
          </div>
        </div>

        {/* View Switcher Tabs & New Plan Button */}
        <div className="flex items-center space-x-3">
          <div className="bg-slate-800/80 p-1 rounded-xl flex items-center border border-slate-700/60">
            <button
              onClick={() => setActiveTab('calendar')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-all ${
                activeTab === 'calendar'
                  ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/30'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <CalendarIcon className="w-3.5 h-3.5" />
              Calendar View
            </button>
            <button
              onClick={() => setActiveTab('flashcards')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-all ${
                activeTab === 'flashcards'
                  ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/30'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Layers className="w-3.5 h-3.5" />
              FSRS Flashcards
            </button>
            <button
              onClick={() => setActiveTab('pomodoro')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-all ${
                activeTab === 'pomodoro'
                  ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/30'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Clock className="w-3.5 h-3.5" />
              Pomodoro ({formatTimer(pomodoroSeconds)})
            </button>
          </div>

          <button
            onClick={() => setShowCreateModal(true)}
            className="flex items-center space-x-2 bg-gradient-to-r from-indigo-500 to-purple-600 hover:from-indigo-600 hover:to-purple-700 text-white px-4 py-2 rounded-xl text-xs font-semibold shadow-lg shadow-indigo-500/20 transition-all cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>New Study Plan</span>
          </button>
        </div>
      </div>

      {/* Notification Toast */}
      {actionMessage && (
        <div className="mx-8 mt-4 p-3 rounded-xl bg-indigo-500/10 border border-indigo-500/30 text-indigo-300 text-sm flex items-center justify-between">
          <span>{actionMessage}</span>
          <button onClick={() => setActionMessage(null)} className="text-slate-400 hover:text-white">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Main Content Area */}
      <div className="p-8 flex-1">
        {activeTab === 'calendar' && (
          <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
            {/* Left Sidebar: Active Plan Details & Controls */}
            <div className="lg:col-span-1 space-y-5">
              {/* Active Plan Selector Card */}
              <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5 shadow-xl">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400">Current Plan</h3>
                  <span className="text-xs font-semibold text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-full border border-emerald-500/20">
                    {selectedPlan?.status || 'Active'}
                  </span>
                </div>

                {isLoading ? (
                  <div className="text-xs text-indigo-400 py-3 flex items-center gap-2">
                    <span className="animate-spin">⏳</span> Loading study plans...
                  </div>
                ) : error ? (
                  <div className="text-xs text-rose-400 py-2 mb-2 bg-rose-500/10 border border-rose-500/20 rounded-lg p-2">
                    {error}
                  </div>
                ) : plans.length > 0 ? (
                  <select
                    value={selectedPlan?.id || ''}
                    onChange={(e) => {
                      const p = plans.find((x) => x.id === e.target.value);
                      if (p) setSelectedPlan(p);
                    }}
                    className="w-full bg-slate-800/80 border border-slate-700 rounded-xl px-3 py-2 text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500 mb-4"
                  >
                    {plans.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.title} (Exam: {p.examDate})
                      </option>
                    ))}
                  </select>
                ) : (
                  <div className="text-sm text-slate-400 mb-4">No active plans found. Create one to begin!</div>
                )}

                {selectedPlan && (
                  <div className="space-y-3 pt-2 border-t border-slate-800/80 text-xs text-slate-300">
                    <div className="flex justify-between items-center">
                      <span className="text-slate-400">Exam Target:</span>
                      <span className="font-semibold text-rose-400 flex items-center gap-1">
                        <Target className="w-3.5 h-3.5" />
                        {selectedPlan.examDate}
                      </span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-slate-400">Pace:</span>
                      <span className="font-semibold text-slate-200">{selectedPlan.hoursPerDay} hrs / day</span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-slate-400">Days Remaining:</span>
                      <span className="font-semibold text-amber-400">{selectedPlan.daysToExam} days</span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-slate-400">Completed Tasks:</span>
                      <span className="font-semibold text-emerald-400">
                        {selectedPlan.completedTasks} / {selectedPlan.totalTasks}
                      </span>
                    </div>

                    {/* Progress Bar */}
                    <div className="pt-2">
                      <div className="flex justify-between text-[11px] mb-1">
                        <span className="text-slate-400">Completion</span>
                        <span className="font-semibold text-indigo-400">
                          {Math.round(selectedPlan.progressPercentage)}%
                        </span>
                      </div>
                      <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-gradient-to-r from-indigo-500 to-purple-500 rounded-full transition-all duration-500"
                          style={{ width: `${selectedPlan.progressPercentage}%` }}
                        />
                      </div>
                    </div>
                  </div>
                )}
              </div>

              {/* Adaptive Controls Card */}
              <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5 shadow-xl space-y-3">
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-2">
                  Smart Optimization
                </h3>
                <button
                  onClick={handleRunCatchUp}
                  disabled={!selectedPlan}
                  className="w-full flex items-center justify-between px-3 py-2.5 rounded-xl bg-slate-800/80 hover:bg-slate-800 text-xs font-semibold text-amber-300 border border-amber-500/20 transition-all disabled:opacity-50 cursor-pointer"
                >
                  <span className="flex items-center gap-2">
                    <RotateCcw className="w-3.5 h-3.5" />
                    Adaptive Catch-Up
                  </span>
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>

                <button
                  onClick={handleInjectWeakTopics}
                  disabled={!selectedPlan}
                  className="w-full flex items-center justify-between px-3 py-2.5 rounded-xl bg-slate-800/80 hover:bg-slate-800 text-xs font-semibold text-purple-300 border border-purple-500/20 transition-all disabled:opacity-50 cursor-pointer"
                >
                  <span className="flex items-center gap-2">
                    <Brain className="w-3.5 h-3.5" />
                    Sync Weak Aptitude Topics
                  </span>
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>

                <button
                  onClick={() => handleStartPomodoro()}
                  className="w-full flex items-center justify-between px-3 py-2.5 rounded-xl bg-indigo-500/10 hover:bg-indigo-500/20 text-xs font-semibold text-indigo-300 border border-indigo-500/30 transition-all cursor-pointer"
                >
                  <span className="flex items-center gap-2">
                    <Play className="w-3.5 h-3.5 text-indigo-400" />
                    Start 25m Focus Block
                  </span>
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>
              </div>

              {/* Legend Card */}
              <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5 shadow-xl text-xs space-y-2">
                <h3 className="font-bold uppercase tracking-wider text-slate-400 mb-2">Task Colors</h3>
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded bg-blue-500" />
                  <span className="text-slate-300">New Study Topic</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded bg-purple-500" />
                  <span className="text-slate-300">Spaced Revision</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded bg-amber-500" />
                  <span className="text-slate-300">Buffer / Catch-up</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded bg-emerald-500" />
                  <span className="text-slate-300">Completed (Done)</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded bg-rose-500" />
                  <span className="text-slate-300">Exam Milestone</span>
                </div>
              </div>
            </div>

            {/* Right Main Calendar View */}
            <div className="lg:col-span-3 bg-slate-900/70 border border-slate-800 rounded-2xl p-6 shadow-2xl overflow-hidden">
              <style>{`
                .fc {
                  --fc-border-color: #334155;
                  --fc-page-bg-color: transparent;
                  --fc-neutral-bg-color: #1e293b;
                  --fc-list-event-hover-bg-color: #334155;
                  --fc-theme-standard-border-color: #334155;
                  font-family: inherit;
                }
                .fc .fc-toolbar-title {
                  font-size: 1.15rem;
                  font-weight: 700;
                  color: #f1f5f9;
                }
                .fc .fc-button {
                  background-color: #1e293b;
                  border-color: #475569;
                  color: #e2e8f0;
                  font-size: 0.8rem;
                  font-weight: 600;
                  border-radius: 0.5rem;
                  padding: 0.4rem 0.8rem;
                  box-shadow: none;
                }
                .fc .fc-button:hover {
                  background-color: #334155;
                  border-color: #64748b;
                  color: #ffffff;
                }
                .fc .fc-button-active {
                  background-color: #4f46e5 !important;
                  border-color: #6366f1 !important;
                  color: #ffffff !important;
                }
                .fc-theme-standard td, .fc-theme-standard th {
                  border-color: #1e293b;
                }
                .fc-daygrid-day-number {
                  color: #94a3b8;
                  font-size: 0.8rem;
                  font-weight: 600;
                  padding: 4px 8px !important;
                }
                .fc-col-header-cell-cushion {
                  color: #cbd5e1;
                  font-size: 0.75rem;
                  font-weight: 700;
                  text-transform: uppercase;
                  letter-spacing: 0.05em;
                  padding: 8px !important;
                }
                .fc-event {
                  border-radius: 6px;
                  padding: 2px 6px;
                  font-size: 0.75rem;
                  font-weight: 600;
                  cursor: pointer;
                  box-shadow: 0 2px 4px rgba(0,0,0,0.3);
                  transition: transform 0.15s ease;
                }
                .fc-event:hover {
                  transform: translateY(-1px);
                  filter: brightness(1.1);
                }
              `}</style>
              <FullCalendar
                plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
                initialView="dayGridMonth"
                headerToolbar={{
                  left: 'prev,next today',
                  center: 'title',
                  right: 'dayGridMonth,timeGridWeek,timeGridDay'
                }}
                events={calendarEvents}
                eventClick={handleEventClick}
                height="auto"
                aspectRatio={1.6}
                dayMaxEvents={3}
                nowIndicator={true}
              />
            </div>
          </div>
        )}

        {/* Tab 2: FSRS Spaced Repetition Flashcards */}
        {activeTab === 'flashcards' && (
          <div className="max-w-4xl mx-auto space-y-6">
            {/* Deck Selector Header */}
            <div className="flex flex-wrap items-center justify-between gap-4 bg-slate-900/60 border border-slate-800 p-5 rounded-2xl shadow-xl">
              <div>
                <h2 className="text-lg font-bold text-white flex items-center gap-2">
                  <Brain className="w-5 h-5 text-indigo-400" />
                  {currentDeck.title}
                </h2>
                <p className="text-xs text-slate-400">{currentDeck.description}</p>
              </div>

              {/* Decks Pill Switcher */}
              <div className="flex items-center space-x-2">
                {decks.map((d) => (
                  <button
                    key={d.id}
                    onClick={() => {
                      setActiveDeckId(d.id);
                      setActiveCardIndex(0);
                      setIsFlipped(false);
                    }}
                    className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
                      d.id === activeDeckId
                        ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/30'
                        : 'bg-slate-800 text-slate-400 hover:text-white'
                    }`}
                  >
                    {d.title}
                  </button>
                ))}
              </div>
            </div>

            {/* Flashcard Component */}
            {currentCard ? (
              <div className="space-y-6">
                {/* 3D Flip Card Container */}
                <div
                  onClick={() => setIsFlipped(!isFlipped)}
                  className="relative w-full h-80 rounded-3xl cursor-pointer perspective-1000 transition-all group"
                >
                  <div
                    className={`w-full h-full rounded-3xl border border-slate-800 p-8 flex flex-col justify-between shadow-2xl transition-all duration-500 transform ${
                      isFlipped
                        ? 'bg-gradient-to-br from-slate-900 via-indigo-950/40 to-slate-900 border-indigo-500/40'
                        : 'bg-slate-900/90 hover:border-slate-700'
                    }`}
                  >
                    {/* Card Header metadata */}
                    <div className="flex items-center justify-between text-xs">
                      <span className="px-2.5 py-1 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 font-semibold">
                        {currentCard.topic}
                      </span>
                      <span className="text-slate-400 font-medium">
                        Card {activeCardIndex + 1} of {currentDeck.cards.length} •{' '}
                        {isFlipped ? 'Answer View' : 'Question (Click to flip)'}
                      </span>
                    </div>

                    {/* Card Body */}
                    <div className="my-auto text-center px-4">
                      {isFlipped ? (
                        <div className="space-y-3">
                          <p className="text-xs uppercase font-bold tracking-wider text-indigo-400">Answer & Explanation</p>
                          <p className="text-base text-slate-100 leading-relaxed font-medium whitespace-pre-line">
                            {currentCard.back}
                          </p>
                        </div>
                      ) : (
                        <div className="space-y-2">
                          <p className="text-xs uppercase font-bold tracking-wider text-slate-400">Concept Prompt</p>
                          <h3 className="text-lg font-bold text-white leading-snug">
                            {currentCard.front}
                          </h3>
                        </div>
                      )}
                    </div>

                    {/* Card Footer status info */}
                    <div className="flex items-center justify-between text-xs text-slate-400 pt-4 border-t border-slate-800/80">
                      <span>Reviews: {currentCard.historyCount}</span>
                      <span className="text-indigo-400 font-semibold">
                        {isFlipped ? 'Rate difficulty below' : 'Click anywhere on card to reveal answer'}
                      </span>
                      <span>Difficulty: {currentCard.cardState?.difficulty?.toFixed(1) || '0.0'}</span>
                    </div>
                  </div>
                </div>

                {/* FSRS Rating Buttons (Visible only when flipped) */}
                {isFlipped ? (
                  <div className="grid grid-cols-4 gap-3 bg-slate-900/80 p-4 rounded-2xl border border-slate-800 shadow-xl">
                    {/* Again (1) */}
                    <button
                      onClick={() => handleRateCard(Rating.Again)}
                      className="flex flex-col items-center justify-center p-3 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 text-rose-300 font-semibold text-xs transition-all cursor-pointer group"
                    >
                      <span className="font-bold text-sm">Again</span>
                      <span className="text-[10px] text-rose-400/80 mt-1">
                        Due: {fsrsPreviewSchedule?.again ? formatIntervalDays(fsrsPreviewSchedule.again.card.due) : '< 1d'}
                      </span>
                    </button>

                    {/* Hard (2) */}
                    <button
                      onClick={() => handleRateCard(Rating.Hard)}
                      className="flex flex-col items-center justify-center p-3 rounded-xl bg-amber-500/10 hover:bg-amber-500/20 border border-amber-500/30 text-amber-300 font-semibold text-xs transition-all cursor-pointer group"
                    >
                      <span className="font-bold text-sm">Hard</span>
                      <span className="text-[10px] text-amber-400/80 mt-1">
                        Due: {fsrsPreviewSchedule?.hard ? formatIntervalDays(fsrsPreviewSchedule.hard.card.due) : '1d'}
                      </span>
                    </button>

                    {/* Good (3) */}
                    <button
                      onClick={() => handleRateCard(Rating.Good)}
                      className="flex flex-col items-center justify-center p-3 rounded-xl bg-blue-500/10 hover:bg-blue-500/20 border border-blue-500/30 text-blue-300 font-semibold text-xs transition-all cursor-pointer group"
                    >
                      <span className="font-bold text-sm">Good</span>
                      <span className="text-[10px] text-blue-400/80 mt-1">
                        Due: {fsrsPreviewSchedule?.good ? formatIntervalDays(fsrsPreviewSchedule.good.card.due) : '3d'}
                      </span>
                    </button>

                    {/* Easy (4) */}
                    <button
                      onClick={() => handleRateCard(Rating.Easy)}
                      className="flex flex-col items-center justify-center p-3 rounded-xl bg-emerald-500/10 hover:bg-emerald-500/20 border border-emerald-500/30 text-emerald-300 font-semibold text-xs transition-all cursor-pointer group"
                    >
                      <span className="font-bold text-sm">Easy</span>
                      <span className="text-[10px] text-emerald-400/80 mt-1">
                        Due: {fsrsPreviewSchedule?.easy ? formatIntervalDays(fsrsPreviewSchedule.easy.card.due) : '5d'}
                      </span>
                    </button>
                  </div>
                ) : (
                  <div className="text-center text-xs text-slate-500 italic">
                    Reveal the answer to rate with the FSRS memory engine
                  </div>
                )}
              </div>
            ) : (
              <div className="text-center py-16 bg-slate-900/60 rounded-2xl border border-slate-800">
                <Brain className="w-10 h-10 text-slate-600 mx-auto mb-3" />
                <p className="text-slate-400">No cards in this deck.</p>
              </div>
            )}
          </div>
        )}

        {/* Tab 3: Pomodoro Focus Timer */}
        {activeTab === 'pomodoro' && (
          <div className="max-w-md mx-auto text-center space-y-6 pt-6">
            <div className="bg-slate-900/70 border border-slate-800 p-8 rounded-3xl shadow-2xl space-y-6">
              <div className="p-3 w-16 h-16 rounded-2xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 mx-auto flex items-center justify-center">
                <Clock className="w-8 h-8" />
              </div>

              <div>
                <h2 className="text-xl font-bold text-white">Focus Pomodoro</h2>
                <p className="text-xs text-slate-400 mt-1">
                  {selectedTask ? `Focused on: ${selectedTask.topic}` : 'Deep work sprint (25 minutes)'}
                </p>
              </div>

              {/* Huge Timer Display */}
              <div className="text-6xl font-mono font-bold tracking-tight text-white py-4 bg-slate-950/60 rounded-2xl border border-slate-800">
                {formatTimer(pomodoroSeconds)}
              </div>

              {/* Timer Controls */}
              <div className="flex items-center justify-center space-x-4">
                <button
                  onClick={() => setIsTimerRunning(!isTimerRunning)}
                  className={`p-4 rounded-2xl font-bold text-white shadow-lg transition-all cursor-pointer ${
                    isTimerRunning
                      ? 'bg-amber-600 hover:bg-amber-700 shadow-amber-600/30'
                      : 'bg-indigo-600 hover:bg-indigo-700 shadow-indigo-600/30'
                  }`}
                >
                  {isTimerRunning ? <Pause className="w-6 h-6" /> : <Play className="w-6 h-6 ml-0.5" />}
                </button>

                <button
                  onClick={() => {
                    setIsTimerRunning(false);
                    setPomodoroSeconds(25 * 60);
                  }}
                  className="p-4 rounded-2xl bg-slate-800 hover:bg-slate-700 text-slate-300 font-bold transition-all cursor-pointer"
                >
                  <RotateCcw className="w-6 h-6" />
                </button>
              </div>

              {selectedTask && (
                <div className="pt-4 border-t border-slate-800/80 text-left">
                  <div className="flex justify-between items-center text-xs">
                    <span className="text-slate-400">Associated Task:</span>
                    <button
                      onClick={() => handleUpdateTaskStatus(selectedTask.id, 'DONE')}
                      className="px-2.5 py-1 bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 rounded-lg text-xs font-semibold hover:bg-emerald-500/20 flex items-center gap-1 cursor-pointer"
                    >
                      <Check className="w-3 h-3" />
                      Mark Task Done
                    </button>
                  </div>
                  <p className="text-sm font-semibold text-slate-200 mt-1">{selectedTask.topic}</p>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Task Detail Modal */}
      {selectedTask && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 max-w-md w-full shadow-2xl space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <span className="text-xs px-2.5 py-0.5 rounded-full font-semibold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                {selectedTask.type}
              </span>
              <button onClick={() => setSelectedTask(null)} className="text-slate-400 hover:text-white">
                <X className="w-4 h-4" />
              </button>
            </div>

            <div>
              <h3 className="text-base font-bold text-white">{selectedTask.topic}</h3>
              <p className="text-xs text-slate-400 mt-1">
                Date: {selectedTask.taskDate} • Target Duration: {selectedTask.plannedMinutes} mins
              </p>
            </div>

            <div className="pt-2 flex flex-col gap-2">
              <button
                onClick={() => handleUpdateTaskStatus(selectedTask.id, 'DONE')}
                className="w-full py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-xs flex items-center justify-center gap-2 transition-all cursor-pointer"
              >
                <CheckCircle2 className="w-4 h-4" />
                Mark Completed (Done)
              </button>

              <button
                onClick={() => handleStartPomodoro(selectedTask)}
                className="w-full py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs flex items-center justify-center gap-2 transition-all cursor-pointer"
              >
                <Clock className="w-4 h-4" />
                Launch Pomodoro Session
              </button>

              <button
                onClick={() => handleUpdateTaskStatus(selectedTask.id, 'SKIPPED')}
                className="w-full py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold text-xs transition-all cursor-pointer"
              >
                Skip Task
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create New Plan Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 max-w-xl w-full shadow-2xl space-y-5 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h2 className="text-base font-bold text-white flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-indigo-400" />
                Generate Adaptive Study Plan
              </h2>
              <button onClick={() => setShowCreateModal(false)} className="text-slate-400 hover:text-white">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleCreatePlan} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Plan Title</label>
                <input
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="e.g. GATE CS 2027 Comprehensive Syllabus"
                  required
                  className="w-full bg-slate-800 border border-slate-700 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">Exam Date</label>
                  <input
                    type="date"
                    value={examDate}
                    onChange={(e) => setExamDate(e.target.value)}
                    required
                    className="w-full bg-slate-800 border border-slate-700 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">
                    Daily Study Target: {hoursPerDay} hrs
                  </label>
                  <input
                    type="range"
                    min="1"
                    max="8"
                    step="0.5"
                    value={hoursPerDay}
                    onChange={(e) => setHoursPerDay(parseFloat(e.target.value))}
                    className="w-full accent-indigo-500 mt-2"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Syllabus Topics (One per line or outline)
                </label>
                <textarea
                  rows={4}
                  value={syllabusText}
                  onChange={(e) => setSyllabusText(e.target.value)}
                  className="w-full bg-slate-800 border border-slate-700 rounded-xl p-3 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500 font-mono"
                />
              </div>

              {/* Preview Button & Results */}
              <div className="p-4 bg-slate-950/60 rounded-xl border border-slate-800 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs text-slate-400">Preview Pacing Engine</span>
                  <button
                    type="button"
                    onClick={handlePreviewPlan}
                    disabled={isPreviewing}
                    className="px-3 py-1 bg-indigo-500/10 hover:bg-indigo-500/20 text-indigo-400 border border-indigo-500/20 rounded-lg text-xs font-semibold transition-all cursor-pointer"
                  >
                    {isPreviewing ? 'Calculating...' : 'Calculate Preview'}
                  </button>
                </div>

                {previewResult && (
                  <div className="text-xs space-y-1 text-slate-300 pt-2 border-t border-slate-800">
                    <p>Total Days: <span className="font-semibold text-white">{previewResult.totalDays}</span> ({previewResult.studyDays} study, {previewResult.restDays} rest)</p>
                    <p>Total Tasks: <span className="font-semibold text-indigo-400">{previewResult.totalTasks}</span> | Total Hours: <span className="font-semibold text-purple-400">{previewResult.totalHours} hrs</span></p>
                  </div>
                )}
              </div>

              <div className="pt-2 flex justify-end space-x-3">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2 rounded-xl bg-slate-800 text-slate-300 text-xs font-semibold hover:bg-slate-700"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-5 py-2 rounded-xl bg-gradient-to-r from-indigo-500 to-purple-600 text-white text-xs font-bold shadow-lg shadow-indigo-500/20 hover:from-indigo-600 hover:to-purple-700 disabled:opacity-50"
                >
                  {isSubmitting ? 'Generating...' : 'Save & Build Plan'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
