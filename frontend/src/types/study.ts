import { Card } from 'ts-fsrs';

export type PlanStatus = 'ACTIVE' | 'ARCHIVED';
export type TaskType = 'STUDY' | 'REVISION' | 'APTITUDE_REVISION' | 'BUFFER';
export type TaskStatus = 'PENDING' | 'DONE' | 'SKIPPED';

export interface StudyTaskDto {
  id: string;
  planId: string;
  taskDate: string; // YYYY-MM-DD
  topic: string;
  type: TaskType;
  plannedMinutes: number;
  status: TaskStatus;
  completedAt?: string;
  sourceTaskId?: string;
}

export interface StudyPlanDto {
  id: string;
  title: string;
  status: PlanStatus;
  examDate: string;
  hoursPerDay: number;
  timezone: string;
  daysToExam: number;
  progressPercentage: number;
  totalTasks: number;
  completedTasks: number;
  tasksByDate: Record<string, StudyTaskDto[]>;
  createdAt: string;
}

export interface TopicWeight {
  name: string;
  weight: number;
  estimatedMinutes?: number;
}

export interface PreviewPlanRequest {
  syllabusText?: string;
  topics?: TopicWeight[];
  startDate?: string;
  examDate: string;
  hoursPerDay?: number;
  restWeekdays?: string[];
}

export interface CreatePlanRequest extends PreviewPlanRequest {
  title: string;
}

export interface ScheduleDay {
  date: string;
  isRestDay: boolean;
  tasks: StudyTaskDto[];
  totalMinutes: number;
}

export interface ScheduleResult {
  days: ScheduleDay[];
  totalDays: number;
  studyDays: number;
  restDays: number;
  totalTasks: number;
  totalHours: number;
  unassignedTopics: string[];
}

export interface UpdateTaskStatusRequest {
  status: TaskStatus;
}

export interface CatchUpResponse {
  planId: string;
  overdueTasksCount: number;
  rescheduledTasksCount: number;
  extendedExamDate?: string;
  message: string;
}

export interface PomodoroSessionDto {
  id: string;
  taskId?: string;
  startedAt: string;
  plannedSeconds: number;
  completedAt?: string;
  counted: boolean;
}

export interface PomodoroStartRequest {
  taskId?: string;
  plannedSeconds?: number;
}

// FSRS Flashcard Types
export interface Flashcard {
  id: string;
  deckId: string;
  front: string;
  back: string;
  topic: string;
  cardState: Card;
  historyCount: number;
}

export interface FlashcardDeck {
  id: string;
  title: string;
  description: string;
  topic: string;
  cardCount: number;
  cards: Flashcard[];
}
