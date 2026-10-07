import api from './api';

export interface DocumentDto {
  id: number;
  fileName: string;
  subject: string;
  status: 'PROCESSING' | 'INDEXED' | 'ERROR';
  fileSize: number;
  chunkCount: number;
  createdAt: string;
}

export interface ChunkDto {
  id: number;
  chunkIndex: number;
  content: string;
  tokenCount: number;
}

export const documentService = {
  async getDocuments(subject?: string, query?: string): Promise<DocumentDto[]> {
    const response = await api.get('/api/documents', { params: { subject, query } });
    return response.data;
  },

  async getChunks(documentId: number): Promise<ChunkDto[]> {
    const response = await api.get(`/api/documents/${documentId}/chunks`);
    return response.data;
  },

  async deleteDocument(documentId: number): Promise<void> {
    await api.delete(`/api/documents/${documentId}`);
  },

  async uploadDocument(file: File, subject?: string): Promise<DocumentDto> {
    const formData = new FormData();
    formData.append('file', file);
    if (subject) {
      formData.append('subject', subject);
    }
    const response = await api.post('/api/documents/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },
};
