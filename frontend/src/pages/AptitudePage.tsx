import React, { useState, useEffect } from 'react';
import {
  Compass,
  CheckCircle2,
  XCircle,
  Clock,
  AlertCircle,
  ChevronRight,
  ChevronLeft,
  RotateCcw,
  Sparkles,
  TrendingDown
} from 'lucide-react';
import {
  fetchTracks,
  startAttempt,
  submitAnswer,
  submitAttempt,
  fetchAttemptHistory,
  fetchAptitudeAnalytics,
  requestAiExplanation
} from '../api/aptitude';
import {
  TrackSummary,
  AptitudeTrack,
  AttemptMode,
  AptitudeAttemptDetail,
  AptitudeAttemptSummary,
  AptitudeAnalytics,
  AnswerFeedback
} from '../types/aptitude';

export const AptitudePage: React.FC = () => {
  // State: navigation
  const [activeTab, setActiveTab] = useState<'tracks' | 'test' | 'summary' | 'history' | 'analytics'>('tracks');

  // Tracks & Configuration
  const [tracks, setTracks] = useState<TrackSummary[]>([]);
  const [selectedMode, setSelectedMode] = useState<AttemptMode>('PRACTICE');
  const [questionCount, setQuestionCount] = useState<number>(10);
  const [timeLimitMinutes, setTimeLimitMinutes] = useState<number>(10);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Active Test State
  const [currentAttempt, setCurrentAttempt] = useState<AptitudeAttemptDetail | null>(null);
  const [currentIndex, setCurrentIndex] = useState<number>(0);
  const [userAnswers, setUserAnswers] = useState<Record<string, string>>({});
  const [practiceFeedbacks, setPracticeFeedbacks] = useState<Record<string, AnswerFeedback>>({});
  const [remainingSeconds, setRemainingSeconds] = useState<number>(0);

  // Completed Test State
  const [summaryData, setSummaryData] = useState<AptitudeAttemptSummary | null>(null);
  const [historyList, setHistoryList] = useState<AptitudeAttemptSummary[]>([]);
  const [analytics, setAnalytics] = useState<AptitudeAnalytics | null>(null);

  // AI Explanation State
  const [aiExplanations, setAiExplanations] = useState<Record<string, string>>({});
  const [aiLoading, setAiLoading] = useState<Record<string, boolean>>({});

  useEffect(() => {
    loadInitialData();
  }, []);

  const loadInitialData = async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const [tracksData, analyticsData, historyData] = await Promise.allSettled([
        fetchTracks(),
        fetchAptitudeAnalytics(),
        fetchAttemptHistory()
      ]);

      if (tracksData.status === 'fulfilled') setTracks(tracksData.value);
      if (analyticsData.status === 'fulfilled') setAnalytics(analyticsData.value);
      if (historyData.status === 'fulfilled') setHistoryList(historyData.value);
    } catch (err: any) {
      setErrorMessage(err?.response?.data?.message || 'Failed to load aptitude tracks.');
    } finally {
      setIsLoading(false);
    }
  };

  // Timer Countdown for Mock Tests
  useEffect(() => {
    if (activeTab !== 'test' || selectedMode !== 'MOCK_TEST' || remainingSeconds <= 0) return;

    const timer = setInterval(() => {
      setRemainingSeconds((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          handleAutoSubmit();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [activeTab, selectedMode, remainingSeconds]);

  const handleStartAttempt = async (track: AptitudeTrack) => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const limitSec = selectedMode === 'MOCK_TEST' ? timeLimitMinutes * 60 : 0;
      const attempt = await startAttempt({
        track,
        mode: selectedMode,
        questionCount,
        timeLimitSeconds: limitSec
      });

      setCurrentAttempt(attempt);
      setCurrentIndex(0);
      setUserAnswers({});
      setPracticeFeedbacks({});
      setAiExplanations({});
      setRemainingSeconds(limitSec);
      setActiveTab('test');
    } catch (err: any) {
      setErrorMessage(err?.response?.data?.message || 'Could not start practice session.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSelectOption = async (optionLetter: string) => {
    if (!currentAttempt) return;
    const currentQ = currentAttempt.questions[currentIndex];
    if (!currentQ) return;

    setUserAnswers((prev) => ({ ...prev, [currentQ.id]: optionLetter }));

    if (selectedMode === 'PRACTICE') {
      try {
        const feedback = await submitAnswer(currentAttempt.id, {
          questionId: currentQ.id,
          selectedOption: optionLetter,
          timeSpentSeconds: 15
        });
        setPracticeFeedbacks((prev) => ({ ...prev, [currentQ.id]: feedback }));
      } catch (err: any) {
        setErrorMessage(err?.response?.data?.message || 'Failed to record answer.');
      }
    }
  };

  const handleSubmitTest = async () => {
    if (!currentAttempt) return;
    setIsLoading(true);
    setErrorMessage(null);
    try {
      // In mock test mode, save all unsaved selections first
      if (selectedMode === 'MOCK_TEST') {
        const submitPromises = Object.entries(userAnswers).map(([qId, opt]) =>
          submitAnswer(currentAttempt.id, {
            questionId: qId,
            selectedOption: opt,
            timeSpentSeconds: 20
          })
        );
        await Promise.allSettled(submitPromises);
      }

      const summary = await submitAttempt(currentAttempt.id);
      setSummaryData(summary);
      setActiveTab('summary');
      // Refresh analytics in background
      fetchAptitudeAnalytics().then(setAnalytics).catch(() => {});
    } catch (err: any) {
      setErrorMessage(err?.response?.data?.message || 'Error submitting test attempt.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleAutoSubmit = () => {
    alert('Time limit reached! Your test is automatically submitting.');
    handleSubmitTest();
  };

  const handleAskAi = async (questionId: string) => {
    setAiLoading((prev) => ({ ...prev, [questionId]: true }));
    try {
      const res = await requestAiExplanation({ questionId });
      setAiExplanations((prev) => ({ ...prev, [questionId]: res.explanation }));
    } catch (err: any) {
      setAiExplanations((prev) => ({
        ...prev,
        [questionId]: 'Offline AI Tutor: Break this question down step-by-step from given conditions to eliminate contradictory options.'
      }));
    } finally {
      setAiLoading((prev) => ({ ...prev, [questionId]: false }));
    }
  };

  const formatTime = (secs: number) => {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-6 md:p-8 space-y-8 font-sans">
      {/* Header Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-6">
        <div>
          <div className="flex items-center space-x-3 mb-2">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-cyan-500 to-blue-600 flex items-center justify-center shadow-lg shadow-cyan-500/20">
              <Compass className="w-5 h-5 text-white" />
            </div>
            <h1 className="text-2xl md:text-3xl font-extrabold tracking-tight bg-gradient-to-r from-white via-slate-200 to-slate-400 bg-clip-text text-transparent">
              Aptitude Engine
            </h1>
          </div>
          <p className="text-sm text-slate-400">
            Curated benchmark question banks for Quantitative, Logical, Verbal, and Core CS with server-timed tests.
          </p>
        </div>

        {/* Tab Navigation */}
        <div className="flex bg-slate-900 border border-slate-800 p-1 rounded-xl space-x-1 text-sm font-medium">
          <button
            onClick={() => setActiveTab('tracks')}
            className={`px-4 py-2 rounded-lg transition-all ${
              activeTab === 'tracks'
                ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/30'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Tracks
          </button>
          <button
            onClick={async () => {
              setActiveTab('history');
              const h = await fetchAttemptHistory();
              setHistoryList(h);
            }}
            className={`px-4 py-2 rounded-lg transition-all ${
              activeTab === 'history'
                ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/30'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            History
          </button>
          <button
            onClick={async () => {
              setActiveTab('analytics');
              const a = await fetchAptitudeAnalytics();
              setAnalytics(a);
            }}
            className={`px-4 py-2 rounded-lg transition-all ${
              activeTab === 'analytics'
                ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/30'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Analytics & Weak Topics
          </button>
        </div>
      </div>

      {errorMessage && (
        <div className="p-4 rounded-xl bg-red-950/40 border border-red-800/80 text-red-300 text-sm flex items-center space-x-3">
          <AlertCircle className="w-5 h-5 text-red-400 flex-shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      {/* TAB 1: TRACKS SELECTION & CONFIGURATION */}
      {activeTab === 'tracks' && (
        <div className="space-y-8">
          {/* Quick Analytics Strip */}
          {analytics && analytics.totalPracticed > 0 && (
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div className="bg-slate-900/60 border border-slate-800 p-4 rounded-xl">
                <span className="text-xs text-slate-400">Total Practiced</span>
                <p className="text-2xl font-bold text-slate-100 mt-1">{analytics.totalPracticed}</p>
              </div>
              <div className="bg-slate-900/60 border border-slate-800 p-4 rounded-xl">
                <span className="text-xs text-slate-400">Overall Accuracy</span>
                <p className="text-2xl font-bold text-emerald-400 mt-1">{analytics.overallAccuracyPct}%</p>
              </div>
              <div className="bg-slate-900/60 border border-slate-800 p-4 rounded-xl">
                <span className="text-xs text-slate-400">Completed Tests</span>
                <p className="text-2xl font-bold text-indigo-400 mt-1">{analytics.totalAttempts}</p>
              </div>
              <div className="bg-slate-900/60 border border-slate-800 p-4 rounded-xl">
                <span className="text-xs text-slate-400">Weak Topics Identified</span>
                <p className="text-2xl font-bold text-amber-400 mt-1">{analytics.weakTopics.length}</p>
              </div>
            </div>
          )}

          {/* Practice / Test Mode Controls */}
          <div className="bg-slate-900/40 border border-slate-800 rounded-2xl p-6 space-y-4">
            <h2 className="text-lg font-semibold text-slate-200">Practice Configuration</h2>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {/* Mode Toggle */}
              <div>
                <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider block mb-2">
                  Session Mode
                </label>
                <div className="grid grid-cols-2 gap-2 bg-slate-950 p-1.5 rounded-xl border border-slate-800">
                  <button
                    type="button"
                    onClick={() => setSelectedMode('PRACTICE')}
                    className={`py-2 px-3 text-xs font-semibold rounded-lg transition-all ${
                      selectedMode === 'PRACTICE'
                        ? 'bg-indigo-600 text-white shadow-sm'
                        : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    Practice (Untimed)
                  </button>
                  <button
                    type="button"
                    onClick={() => setSelectedMode('MOCK_TEST')}
                    className={`py-2 px-3 text-xs font-semibold rounded-lg transition-all ${
                      selectedMode === 'MOCK_TEST'
                        ? 'bg-indigo-600 text-white shadow-sm'
                        : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    Mock Test (Timed)
                  </button>
                </div>
              </div>

              {/* Question Count */}
              <div>
                <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider block mb-2">
                  Question Count
                </label>
                <select
                  value={questionCount}
                  onChange={(e) => setQuestionCount(Number(e.target.value))}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500"
                >
                  <option value={5}>5 Questions (Quick Drill)</option>
                  <option value={10}>10 Questions (Standard)</option>
                  <option value={15}>15 Questions (Deep Practice)</option>
                  <option value={20}>20 Questions (Full Mock)</option>
                </select>
              </div>

              {/* Time Limit for Mock */}
              {selectedMode === 'MOCK_TEST' && (
                <div>
                  <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider block mb-2">
                    Time Limit (Minutes)
                  </label>
                  <select
                    value={timeLimitMinutes}
                    onChange={(e) => setTimeLimitMinutes(Number(e.target.value))}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500"
                  >
                    <option value={5}>5 Minutes</option>
                    <option value={10}>10 Minutes</option>
                    <option value={15}>15 Minutes</option>
                    <option value={20}>20 Minutes</option>
                  </select>
                </div>
              )}
            </div>
          </div>

          {/* Track Cards Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {tracks.map((t) => (
              <div
                key={t.track}
                className="bg-slate-900 border border-slate-800 hover:border-slate-700 rounded-2xl p-6 flex flex-col justify-between transition-all group hover:shadow-xl hover:shadow-indigo-500/5"
              >
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs font-bold uppercase tracking-wider px-2.5 py-1 rounded-md bg-indigo-950 text-indigo-400 border border-indigo-800/60">
                      {t.track}
                    </span>
                    <span className="text-xs text-slate-400 font-medium">
                      {t.totalQuestions} Questions Available
                    </span>
                  </div>
                  <h3 className="text-xl font-bold text-slate-100 group-hover:text-indigo-400 transition-colors">
                    {t.title}
                  </h3>
                  <p className="text-sm text-slate-400 mt-2 leading-relaxed">
                    {t.description}
                  </p>

                  {/* Topics Badge Cloud */}
                  {t.topics && t.topics.length > 0 && (
                    <div className="mt-4 flex flex-wrap gap-1.5">
                      {t.topics.slice(0, 6).map((topic) => (
                        <span
                          key={topic}
                          className="text-[11px] px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 font-mono"
                        >
                          {topic}
                        </span>
                      ))}
                      {t.topics.length > 6 && (
                        <span className="text-[11px] px-2 py-0.5 rounded-full bg-slate-800 text-slate-400">
                          +{t.topics.length - 6} more
                        </span>
                      )}
                    </div>
                  )}
                </div>

                <div className="mt-6 pt-4 border-t border-slate-800/80 flex items-center justify-between">
                  <span className="text-xs text-slate-500">
                    {selectedMode === 'MOCK_TEST' ? `Timed (${timeLimitMinutes}m)` : 'Instant Feedback'}
                  </span>
                  <button
                    disabled={isLoading}
                    onClick={() => handleStartAttempt(t.track)}
                    className="inline-flex items-center space-x-2 px-4 py-2 rounded-xl bg-gradient-to-r from-indigo-600 to-indigo-500 hover:from-indigo-500 hover:to-indigo-400 text-white text-sm font-semibold shadow-lg shadow-indigo-600/20 transition-all"
                  >
                    <span>Start Practice</span>
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* TAB 2: ACTIVE TEST / PRACTICE INTERFACE */}
      {activeTab === 'test' && currentAttempt && (
        <div className="max-w-4xl mx-auto space-y-6">
          {/* Top Test Bar */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 flex items-center justify-between">
            <div className="flex items-center space-x-4">
              <span className="text-xs font-bold px-2.5 py-1 rounded-md bg-indigo-950 text-indigo-400 border border-indigo-800/60 uppercase">
                {currentAttempt.track}
              </span>
              <span className="text-sm font-medium text-slate-300">
                Question {currentIndex + 1} of {currentAttempt.totalQuestions}
              </span>
            </div>

            {selectedMode === 'MOCK_TEST' && (
              <div
                className={`flex items-center space-x-2 text-sm font-mono px-3 py-1.5 rounded-lg border ${
                  remainingSeconds < 60
                    ? 'bg-red-950/40 border-red-800 text-red-400 animate-pulse'
                    : 'bg-slate-950 border-slate-800 text-slate-300'
                }`}
              >
                <Clock className="w-4 h-4 text-indigo-400" />
                <span>{formatTime(remainingSeconds)}</span>
              </div>
            )}

            <button
              onClick={handleSubmitTest}
              className="px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-semibold shadow-md shadow-emerald-600/20 transition-all"
            >
              Submit Test
            </button>
          </div>

          {/* Question Display Card */}
          {currentAttempt.questions[currentIndex] && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 md:p-8 space-y-6">
              <div className="flex items-center justify-between text-xs text-slate-400">
                <span className="font-semibold text-indigo-400">
                  Topic: {currentAttempt.questions[currentIndex].topic}
                </span>
                <span className="uppercase">{currentAttempt.questions[currentIndex].difficulty}</span>
              </div>

              <h2 className="text-lg md:text-xl font-medium text-slate-100 leading-relaxed">
                {currentAttempt.questions[currentIndex].question}
              </h2>

              {/* Options List */}
              <div className="space-y-3 pt-2">
                {currentAttempt.questions[currentIndex].options.map((optText, idx) => {
                  const letter = String.fromCharCode(65 + idx); // 'A', 'B', 'C', 'D', 'E'
                  const qId = currentAttempt.questions[currentIndex].id;
                  const isSelected = userAnswers[qId] === letter;
                  const feedback = practiceFeedbacks[qId];

                  let optionStyle = 'border-slate-800 bg-slate-950/60 hover:bg-slate-800/60 text-slate-200';
                  if (isSelected) {
                    optionStyle = 'border-indigo-500 bg-indigo-950/30 text-indigo-200 ring-1 ring-indigo-500';
                  }

                  // Practice mode instant validation colors
                  if (feedback) {
                    if (letter === feedback.correctAnswer) {
                      optionStyle = 'border-emerald-500 bg-emerald-950/40 text-emerald-200 ring-1 ring-emerald-500';
                    } else if (isSelected && !feedback.isCorrect) {
                      optionStyle = 'border-red-500 bg-red-950/40 text-red-200 ring-1 ring-red-500';
                    }
                  }

                  return (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => handleSelectOption(letter)}
                      className={`w-full text-left p-4 rounded-xl border transition-all flex items-start space-x-3 ${optionStyle}`}
                    >
                      <span className="w-6 h-6 rounded-lg bg-slate-800 flex items-center justify-center font-bold text-xs text-slate-300 flex-shrink-0 mt-0.5">
                        {letter}
                      </span>
                      <span className="text-sm font-medium leading-relaxed">{optText}</span>
                    </button>
                  );
                })}
              </div>

              {/* Practice Mode Rationale & AI Tutor */}
              {practiceFeedbacks[currentAttempt.questions[currentIndex].id] && (
                <div className="mt-6 p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                      Standard Solution Rationale
                    </span>
                    <button
                      onClick={() => handleAskAi(currentAttempt.questions[currentIndex].id)}
                      disabled={aiLoading[currentAttempt.questions[currentIndex].id]}
                      className="inline-flex items-center space-x-1.5 text-xs text-indigo-400 hover:text-indigo-300 font-semibold"
                    >
                      <Sparkles className="w-3.5 h-3.5" />
                      <span>{aiLoading[currentAttempt.questions[currentIndex].id] ? 'Generating...' : 'Ask AI Tutor'}</span>
                    </button>
                  </div>
                  <p className="text-sm text-slate-300 leading-relaxed">
                    {practiceFeedbacks[currentAttempt.questions[currentIndex].id].rationale ||
                      'Correct answer: ' + practiceFeedbacks[currentAttempt.questions[currentIndex].id].correctAnswer}
                  </p>

                  {/* AI Explanation Accordion */}
                  {aiExplanations[currentAttempt.questions[currentIndex].id] && (
                    <div className="mt-3 p-3 rounded-lg bg-indigo-950/30 border border-indigo-900/60 text-xs text-indigo-200 leading-relaxed whitespace-pre-line">
                      <span className="font-bold text-indigo-300 block mb-1">AI Tutor Breakdown:</span>
                      {aiExplanations[currentAttempt.questions[currentIndex].id]}
                    </div>
                  )}
                </div>
              )}

              {/* Bottom Question Controls */}
              <div className="flex items-center justify-between pt-4 border-t border-slate-800">
                <button
                  type="button"
                  disabled={currentIndex === 0}
                  onClick={() => setCurrentIndex((prev) => Math.max(0, prev - 1))}
                  className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-200 text-sm font-semibold flex items-center space-x-2"
                >
                  <ChevronLeft className="w-4 h-4" />
                  <span>Previous</span>
                </button>

                {currentIndex < currentAttempt.totalQuestions - 1 ? (
                  <button
                    type="button"
                    onClick={() => setCurrentIndex((prev) => Math.min(currentAttempt.totalQuestions - 1, prev + 1))}
                    className="px-5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-semibold flex items-center space-x-2"
                  >
                    <span>Next</span>
                    <ChevronRight className="w-4 h-4" />
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={handleSubmitTest}
                    className="px-5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-semibold"
                  >
                    Finish & Submit
                  </button>
                )}
              </div>
            </div>
          )}

          {/* Question Jump Palette */}
          <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4 flex flex-wrap gap-2 justify-center">
            {currentAttempt.questions.map((q, idx) => {
              const isAnswered = Boolean(userAnswers[q.id]);
              const isCurrent = idx === currentIndex;
              return (
                <button
                  key={q.id}
                  onClick={() => setCurrentIndex(idx)}
                  className={`w-9 h-9 rounded-lg text-xs font-semibold border transition-all ${
                    isCurrent
                      ? 'border-indigo-500 bg-indigo-600 text-white'
                      : isAnswered
                      ? 'border-emerald-600 bg-emerald-950/40 text-emerald-300'
                      : 'border-slate-800 bg-slate-950 text-slate-400 hover:text-slate-200'
                  }`}
                >
                  {idx + 1}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* TAB 3: TEST REVIEW & SUMMARY */}
      {activeTab === 'summary' && summaryData && (
        <div className="max-w-4xl mx-auto space-y-8">
          {/* Hero Score Card */}
          <div className="bg-gradient-to-br from-slate-900 to-indigo-950/40 border border-indigo-900/40 rounded-2xl p-8 flex flex-col md:flex-row items-center justify-between gap-6">
            <div>
              <span className="text-xs uppercase font-bold text-indigo-400 tracking-wider">
                Attempt Complete
              </span>
              <h2 className="text-3xl font-extrabold text-white mt-1">
                Your Score: {summaryData.scorePct}%
              </h2>
              <p className="text-sm text-slate-300 mt-2">
                You answered {summaryData.correctCount} out of {summaryData.totalQuestions} questions correctly in{' '}
                {formatTime(summaryData.totalTimeSpentSeconds || 0)}.
              </p>
            </div>

            <div className="flex items-center space-x-3">
              <button
                onClick={() => setActiveTab('tracks')}
                className="px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-semibold flex items-center space-x-2"
              >
                <RotateCcw className="w-4 h-4" />
                <span>Practice Again</span>
              </button>
            </div>
          </div>

          {/* Full Question-by-Question Review */}
          <div className="space-y-4">
            <h3 className="text-lg font-bold text-slate-200">Detailed Answer Review</h3>
            {summaryData.questions.map((q, idx) => (
              <div
                key={q.questionId}
                className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4"
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-slate-400">
                    Question {idx + 1} • Topic: {q.topic}
                  </span>
                  {q.isCorrect ? (
                    <span className="inline-flex items-center space-x-1 text-xs font-semibold text-emerald-400 bg-emerald-950/40 px-2.5 py-1 rounded-md border border-emerald-800/60">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>Correct</span>
                    </span>
                  ) : (
                    <span className="inline-flex items-center space-x-1 text-xs font-semibold text-red-400 bg-red-950/40 px-2.5 py-1 rounded-md border border-red-800/60">
                      <XCircle className="w-3.5 h-3.5" />
                      <span>Incorrect</span>
                    </span>
                  )}
                </div>

                <p className="text-base text-slate-100 font-medium">{q.question}</p>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-sm pt-2">
                  <div className="p-3 rounded-xl bg-slate-950 border border-slate-800">
                    <span className="text-xs text-slate-400 block mb-1">Your Selection:</span>
                    <span
                      className={`font-semibold ${
                        q.isCorrect ? 'text-emerald-400' : 'text-red-400'
                      }`}
                    >
                      {q.selectedOption ? `Option ${q.selectedOption}` : 'Skipped / Unanswered'}
                    </span>
                  </div>
                  <div className="p-3 rounded-xl bg-slate-950 border border-slate-800">
                    <span className="text-xs text-slate-400 block mb-1">Correct Answer:</span>
                    <span className="font-semibold text-emerald-400">Option {q.correctAnswer}</span>
                  </div>
                </div>

                {q.rationale && (
                  <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 text-sm text-slate-300 leading-relaxed">
                    <span className="text-xs font-semibold uppercase text-slate-400 block mb-1">
                      Rationale & Explanation:
                    </span>
                    {q.rationale}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* TAB 4: HISTORY */}
      {activeTab === 'history' && (
        <div className="max-w-4xl mx-auto space-y-6">
          <h2 className="text-xl font-bold text-slate-100">Attempt History</h2>
          {historyList.length === 0 ? (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-12 text-center text-slate-400">
              No completed practice attempts yet. Start practicing from the Tracks tab!
            </div>
          ) : (
            <div className="space-y-3">
              {historyList.map((h) => (
                <div
                  key={h.id}
                  className="bg-slate-900 border border-slate-800 rounded-xl p-4 flex items-center justify-between hover:border-slate-700 transition-colors"
                >
                  <div className="space-y-1">
                    <div className="flex items-center space-x-2">
                      <span className="text-xs font-bold px-2 py-0.5 rounded bg-indigo-950 text-indigo-400 border border-indigo-800">
                        {h.track}
                      </span>
                      <span className="text-xs text-slate-400">{h.mode}</span>
                    </div>
                    <p className="text-sm text-slate-300">
                      Score: <strong className="text-emerald-400">{h.scorePct}%</strong> ({h.correctCount}/{h.totalQuestions} Correct)
                    </p>
                  </div>
                  <span className="text-xs text-slate-500">
                    {new Date(h.startedAt).toLocaleDateString()}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 5: ANALYTICS & WEAK TOPICS */}
      {activeTab === 'analytics' && analytics && (
        <div className="max-w-4xl mx-auto space-y-8">
          <div>
            <h2 className="text-xl font-bold text-slate-100">Performance Analytics</h2>
            <p className="text-sm text-slate-400 mt-1">
              Tracks weak topics to recommend targeted revision sessions in the Study Planner.
            </p>
          </div>

          {/* Weak Topics Card */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
            <div className="flex items-center space-x-2 text-amber-400">
              <TrendingDown className="w-5 h-5" />
              <h3 className="text-base font-bold text-slate-200">Weak Topics (Accuracy &lt; 60%)</h3>
            </div>
            {analytics.weakTopics.length === 0 ? (
              <p className="text-sm text-slate-400">
                No weak topics flagged! Keep taking mock tests to pinpoint challenging areas.
              </p>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {analytics.weakTopics.map((wt) => (
                  <div
                    key={wt.topic}
                    className="p-3.5 rounded-xl bg-slate-950 border border-amber-900/40 flex items-center justify-between"
                  >
                    <div>
                      <span className="text-xs font-mono text-amber-300 font-bold">{wt.topic}</span>
                      <span className="text-xs text-slate-500 block">
                        {wt.correctAnswers}/{wt.totalQuestions} questions correct
                      </span>
                    </div>
                    <span className="text-sm font-bold text-amber-400">{wt.accuracyPct}%</span>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* All Practiced Topics Breakdown */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
            <h3 className="text-base font-bold text-slate-200">All Practiced Topics</h3>
            <div className="space-y-2">
              {analytics.allTopics.map((top) => (
                <div
                  key={top.topic}
                  className="p-3 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-between text-sm"
                >
                  <span className="font-mono text-slate-300">{top.topic}</span>
                  <div className="flex items-center space-x-4">
                    <span className="text-xs text-slate-500">
                      {top.correctAnswers} / {top.totalQuestions}
                    </span>
                    <span
                      className={`font-bold ${
                        top.accuracyPct >= 70
                          ? 'text-emerald-400'
                          : top.accuracyPct >= 50
                          ? 'text-amber-400'
                          : 'text-red-400'
                      }`}
                    >
                      {top.accuracyPct}%
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AptitudePage;
