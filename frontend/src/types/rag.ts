export interface Document {
  id: string;
  name: string;
  size: number;
  uploadedAt: string;
  status: 'processing' | 'indexed' | 'failed';
  chunkCount?: number;
}

export interface ChatSource {
  documentId: string;
  documentName: string;
  snippet: string;
  pageNumber?: number;
  score: number;
}

export interface Message {
  id: string;
  sender: 'user' | 'assistant';
  content: string;
  timestamp: string;
  sources?: ChatSource[];
  isStreaming?: boolean;
}
