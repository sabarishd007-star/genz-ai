import { axiosClient } from './axiosClient';
import { Document } from '../types/rag';

export const uploadDocument = async (
  file: File,
  onProgress?: (progress: number) => void
): Promise<Document> => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await axiosClient.post<Document>('/rag/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (progressEvent) => {
      if (progressEvent.total && onProgress) {
        onProgress(Math.round((progressEvent.loaded * 100) / progressEvent.total));
      }
    },
  });
  return response.data;
};

export const getDocuments = async (): Promise<Document[]> => {
  const response = await axiosClient.get<Document[]>('/rag/documents');
  return response.data;
};

export const deleteDocument = async (documentId: string): Promise<void> => {
  await axiosClient.delete(`/rag/documents/${documentId}`);
};

export const sendChatMessage = async (
  query: string,
  selectedDocIds: string[],
  onChunk: (chunk: string) => void
): Promise<void> => {
  const response = await fetch('/api/rag/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
    },
    body: JSON.stringify({ query, documentIds: selectedDocIds }),
  });

  if (!response.body) return;
  const reader = response.body.getReader();
  const decoder = new TextDecoder();

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    onChunk(decoder.decode(value, { stream: true }));
  }
};
