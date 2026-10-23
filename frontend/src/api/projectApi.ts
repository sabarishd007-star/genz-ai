import { axiosClient } from './axiosClient';

export interface ProjectDto {
  id: string;
  title: string;
  domain: string;
  idea: string;
  constraints?: Record<string, any>;
  status: string;
  blueprintVersion?: number;
  source: 'LLM' | 'TEMPLATE';
  blueprintContent: {
    problemStatement?: string;
    features?: {
      mvp: string[];
      later: string[];
    };
    techStack?: Array<{
      layer: string;
      choice: string;
      reason: string;
    }>;
    erDiagram?: string;
    entities?: Array<{
      name: string;
      attributes: string[];
    }>;
    apiEndpoints?: Array<{
      method: string;
      path: string;
      purpose: string;
    }>;
    architectureDiagram?: string;
    roadmap?: {
      phases: Array<{
        name: string;
        milestones: Array<{
          title: string;
          description: string;
        }>;
      }>;
    };
    fileTree?: Array<{
      path: string;
      purpose: string;
    }>;
    risks?: string[];
    vivaQuestions?: Array<{
      question: string;
      modelAnswer: string;
      topic?: string;
    }>;
  };
  milestones: Array<{
    id: string;
    phase: string;
    title: string;
    description: string;
    status: 'TODO' | 'DOING' | 'DONE';
    position: number;
  }>;
  createdAt: string;
  updatedAt: string;
}

export interface VivaQuestionDto {
  id: string;
  blueprintId: string;
  question: string;
  modelAnswer: string;
  topic?: string;
  previousAnswers: Array<{
    id: string;
    answerText: string;
    scores: {
      correctness: number;
      completeness: number;
      clarity: number;
      overall: number;
    };
    feedback: string;
    judgedBy: string;
    createdAt: string;
  }>;
}

export interface VivaAnswerResponse {
  id: string;
  questionId: string;
  answerText: string;
  scores: {
    correctness: number;
    completeness: number;
    clarity: number;
    overall: number;
  };
  feedback: string;
  judgedBy: string;
  createdAt: string;
}

export const projectApi = {
  getProjects: async (): Promise<ProjectDto[]> => {
    const res = await axiosClient.get<ProjectDto[]>('/projects');
    return res.data;
  },

  getProject: async (id: string): Promise<ProjectDto> => {
    const res = await axiosClient.get<ProjectDto>(`/projects/${id}`);
    return res.data;
  },

  createProject: async (data: {
    title: string;
    domain: string;
    idea: string;
    constraints?: Record<string, any>;
    contextDocumentId?: string;
  }): Promise<ProjectDto> => {
    const res = await axiosClient.post<ProjectDto>('/projects', data);
    return res.data;
  },

  regenerateSection: async (id: string, section?: string): Promise<ProjectDto> => {
    const url = section ? `/projects/${id}/regenerate?section=${encodeURIComponent(section)}` : `/projects/${id}/regenerate`;
    const res = await axiosClient.post<ProjectDto>(url);
    return res.data;
  },

  updateMilestone: async (projectId: string, milestoneId: string, status: 'TODO' | 'DOING' | 'DONE') => {
    const res = await axiosClient.patch(`/projects/${projectId}/milestones/${milestoneId}`, { status });
    return res.data;
  },

  getVivaQuestions: async (projectId: string): Promise<VivaQuestionDto[]> => {
    const res = await axiosClient.get<VivaQuestionDto[]>(`/projects/${projectId}/viva`);
    return res.data;
  },

  answerVivaQuestion: async (projectId: string, questionId: string, answerText: string): Promise<VivaAnswerResponse> => {
    const res = await axiosClient.post<VivaAnswerResponse>(`/projects/${projectId}/viva/${questionId}/answer`, { answerText });
    return res.data;
  },

  exportReadme: async (projectId: string): Promise<string> => {
    const res = await axiosClient.get<string>(`/projects/${projectId}/export/readme`, {
      responseType: 'text',
    });
    return res.data;
  },

  exportJdl: async (projectId: string): Promise<string> => {
    const res = await axiosClient.get<string>(`/projects/${projectId}/export/jdl`, {
      responseType: 'text',
    });
    return res.data;
  },
};
