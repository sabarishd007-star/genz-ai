import { axiosClient } from './axiosClient';
import {
  StudyPlanDto,
  CreatePlanRequest,
  PreviewPlanRequest,
  ScheduleResult,
  StudyTaskDto,
  UpdateTaskStatusRequest,
  CatchUpResponse,
  PomodoroSessionDto,
  PomodoroStartRequest
} from '../types/study';

export const fetchStudyPlans = async (): Promise<StudyPlanDto[]> => {
  const res = await axiosClient.get<StudyPlanDto[]>('/study/plans');
  return res.data;
};

export const fetchStudyPlanById = async (id: string): Promise<StudyPlanDto> => {
  const res = await axiosClient.get<StudyPlanDto>(`/study/plans/${id}`);
  return res.data;
};

export const previewStudyPlan = async (request: PreviewPlanRequest): Promise<ScheduleResult> => {
  const res = await axiosClient.post<ScheduleResult>('/study/plans/preview', request);
  return res.data;
};

export const createStudyPlan = async (request: CreatePlanRequest): Promise<StudyPlanDto> => {
  const res = await axiosClient.post<StudyPlanDto>('/study/plans', request);
  return res.data;
};

export const updateStudyTaskStatus = async (
  planId: string,
  taskId: string,
  request: UpdateTaskStatusRequest
): Promise<StudyTaskDto> => {
  const res = await axiosClient.patch<StudyTaskDto>(
    `/study/plans/${planId}/tasks/${taskId}`,
    request
  );
  return res.data;
};

export const triggerCatchUp = async (planId: string): Promise<CatchUpResponse> => {
  const res = await axiosClient.post<CatchUpResponse>(`/study/plans/${planId}/catch-up`);
  return res.data;
};

export const injectWeakTopics = async (planId: string): Promise<{ addedTasksCount: number; status: string }> => {
  const res = await axiosClient.post<{ addedTasksCount: number; status: string }>(
    `/study/plans/${planId}/weak-topics`
  );
  return res.data;
};

export const startPomodoroSession = async (
  request: PomodoroStartRequest
): Promise<PomodoroSessionDto> => {
  const res = await axiosClient.post<PomodoroSessionDto>('/study/pomodoro/start', request);
  return res.data;
};

export const completePomodoroSession = async (sessionId: string): Promise<PomodoroSessionDto> => {
  const res = await axiosClient.post<PomodoroSessionDto>(`/study/pomodoro/${sessionId}/complete`);
  return res.data;
};
