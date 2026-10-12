export type AptitudeTrack = 'QUANTITATIVE' | 'LOGICAL' | 'VERBAL' | 'CORE_CS';
export type AttemptMode = 'PRACTICE' | 'MOCK_TEST';
export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'ABANDONED';

export interface TrackSummary {
  track: AptitudeTrack;
  title: string;
  description: string;
  totalQuestions: number;
  topics: string[];
}

export interface AptitudeQuestion {
  id: string;
  track: AptitudeTrack;
  topic: string;
  difficulty: string;
  question: string;
  options: string[];
}

export interface AptitudeAttemptDetail {
  id: string;
  track: AptitudeTrack;
  mode: AttemptMode;
  totalQuestions: number;
  timeLimitSeconds: number;
  status: AttemptStatus;
  startedAt: string;
  questions: AptitudeQuestion[];
}

export interface SubmitAnswerRequest {
  questionId: string;
  selectedOption: string;
  timeSpentSeconds: number;
}

export interface AnswerFeedback {
  questionId: string;
  selectedOption: string | null;
  isCorrect: boolean | null;
  correctAnswer: string | null;
  rationale: string | null;
  submitted: boolean;
}

export interface AptitudeQuestionReview {
  questionId: string;
  track: AptitudeTrack;
  topic: string;
  difficulty: string;
  question: string;
  options: string[];
  selectedOption: string | null;
  correctAnswer: string;
  isCorrect: boolean;
  timeSpentSeconds: number;
  rationale: string;
}

export interface AptitudeAttemptSummary {
  id: string;
  track: AptitudeTrack;
  mode: AttemptMode;
  totalQuestions: number;
  correctCount: number;
  scorePct: number;
  timeLimitSeconds: number;
  totalTimeSpentSeconds: number;
  status: AttemptStatus;
  startedAt: string;
  completedAt: string;
  questions: AptitudeQuestionReview[];
}

export interface TopicStat {
  topic: string;
  track: string;
  totalQuestions: number;
  correctAnswers: number;
  accuracyPct: number;
}

export interface AptitudeAnalytics {
  totalAttempts: number;
  totalPracticed: number;
  overallAccuracyPct: number;
  trackAccuracy: Record<string, number>;
  weakTopics: TopicStat[];
  strongTopics: TopicStat[];
  allTopics: TopicStat[];
}

export interface ExplainResponse {
  questionId: string;
  explanation: string;
  modelUsed: string;
  isFallback: boolean;
}
