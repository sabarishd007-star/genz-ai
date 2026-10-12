import { axiosClient } from './axiosClient';

export interface DashboardSummary {
  documentsCount: number;
  studyPlansCount: number;
  aptitudeScore: string;
  activeStreakDays: number;
  userTimezone: string;
}

export const fetchDashboardSummary = async (): Promise<DashboardSummary> => {
  const response = await axiosClient.get<DashboardSummary>('/dashboard/summary');
  return response.data;
};
