import { axiosClient } from './axiosClient';
import {
  TrackSummary,
  AptitudeAttemptDetail,
  AptitudeAttemptSummary,
  AnswerFeedback,
  AptitudeAnalytics,
  ExplainResponse,
  AptitudeTrack,
  AttemptMode
} from '../types/aptitude';

export const fetchTracks = async (): Promise<TrackSummary[]> => {
  const res = await axiosClient.get<TrackSummary[]>('/aptitude/tracks');
  return res.data;
};

export const startAttempt = async (payload: {
  track: AptitudeTrack;
  mode?: AttemptMode;
  topic?: string;
  questionCount?: number;
  timeLimitSeconds?: number;
}): Promise<AptitudeAttemptDetail> => {
  const res = await axiosClient.post<AptitudeAttemptDetail>('/aptitude/attempts', payload);
  return res.data;
};

export const submitAnswer = async (
  attemptId: string,
  payload: {
    questionId: string;
    selectedOption: string;
    timeSpentSeconds?: number;
  }
): Promise<AnswerFeedback> => {
  const res = await axiosClient.post<AnswerFeedback>(`/aptitude/attempts/${attemptId}/answer`, payload);
  return res.data;
};

export const submitAttempt = async (attemptId: string): Promise<AptitudeAttemptSummary> => {
  const res = await axiosClient.post<AptitudeAttemptSummary>(`/aptitude/attempts/${attemptId}/submit`);
  return res.data;
};

export const fetchAttemptDetails = async (attemptId: string): Promise<AptitudeAttemptSummary> => {
  const res = await axiosClient.get<AptitudeAttemptSummary>(`/aptitude/attempts/${attemptId}`);
  return res.data;
};

export const fetchAttemptHistory = async (): Promise<AptitudeAttemptSummary[]> => {
  const res = await axiosClient.get<AptitudeAttemptSummary[]>('/aptitude/history');
  return res.data;
};

export const fetchAptitudeAnalytics = async (): Promise<AptitudeAnalytics> => {
  const res = await axiosClient.get<AptitudeAnalytics>('/aptitude/analytics');
  return res.data;
};

export const requestAiExplanation = async (payload: {
  questionId: string;
  userQuery?: string;
}): Promise<ExplainResponse> => {
  const res = await axiosClient.post<ExplainResponse>('/aptitude/explain', payload);
  return res.data;
};
