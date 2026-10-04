import React, { useState, useRef, useEffect } from 'react';
import { DocumentManager } from '../components/DocumentManager';
import { sendChatMessage } from '../api/ragApi';
import { Message } from '../types/rag';
import { Send, Bot, User, Sparkles, BookOpen } from 'lucide-react';

const EmptyState: React.FC = () => (
  <div className="flex flex-col items-center justify-center h-full text-center px-8 py-12">
    <div className="w-14 h-14 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center mb-4">
      <Sparkles className="w-7 h-7 text-indigo-400" />
    </div>
    <h3 className="text-lg font-semibold text-slate-200 mb-2">RAG AI Chat</h3>
    <p className="text-sm text-slate-400 max-w-sm leading-relaxed">
      Upload documents on the left, select them as context, then ask any question. The AI will answer with citations from your material.
    </p>
    <div className="mt-6 grid grid-cols-1 gap-2 w-full max-w-xs">
      {[
        'Summarize the key concepts from my notes',
        'What does chapter 3 explain about algorithms?',
        'List all important dates from the document',
      ].map((hint) => (
        <div
          key={hint}
          className="text-left px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-xs text-slate-400 flex items-center gap-2"
        >
          <BookOpen className="w-3.5 h-3.5 text-indigo-400 shrink-0" />
          {hint}
        </div>
      ))}
    </div>
  </div>
);

const MessageBubble: React.FC<{ msg: Message }> = ({ msg }) => {
  const isUser = msg.sender === 'user';
  return (
    <div className={`flex gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
      <div
        className={`w-7 h-7 rounded-full shrink-0 flex items-center justify-center text-xs font-semibold mt-1 ${
          isUser
            ? 'bg-indigo-600 text-white'
            : 'bg-slate-700 text-slate-300 border border-slate-600'
        }`}
      >
        {isUser ? <User className="w-3.5 h-3.5" /> : <Bot className="w-3.5 h-3.5" />}
      </div>
      <div
        className={`max-w-[78%] rounded-2xl px-4 py-3 text-sm leading-relaxed ${
          isUser
            ? 'bg-indigo-600 text-white rounded-tr-sm'
            : 'bg-slate-800 text-slate-100 border border-slate-700 rounded-tl-sm'
        }`}
      >
        <p className="whitespace-pre-wrap">{msg.content}</p>
        {msg.isStreaming && (
          <span className="inline-block w-2 h-4 bg-indigo-400 animate-pulse ml-0.5 rounded-sm align-middle" />
        )}
        {msg.sources && msg.sources.length > 0 && (
          <div className="mt-3 pt-3 border-t border-slate-600 space-y-1.5">
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Sources</p>
            {msg.sources.map((src, i) => (
              <div key={i} className="bg-slate-900/60 rounded-lg p-2 text-xs text-slate-300">
                <p className="font-medium text-indigo-400 mb-0.5">
                  {src.documentName}
                  {src.pageNumber && <span className="text-slate-500"> · p.{src.pageNumber}</span>}
                </p>
                <p className="text-slate-400 italic">"{src.snippet}"</p>
              </div>
            ))}
          </div>
        )}
        <p className={`text-xs mt-1.5 ${isUser ? 'text-indigo-300' : 'text-slate-500'}`}>
          {msg.timestamp}
        </p>
      </div>
    </div>
  );
};

export const ChatPage: React.FC = () => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [selectedDocIds, setSelectedDocIds] = useState<string[]>([]);
  const [isSending, setIsSending] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleToggleDoc = (id: string) => {
    setSelectedDocIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const handleSend = async () => {
    if (!input.trim() || isSending) return;

    const userMsg: Message = {
      id: `user-${Date.now()}`,
      sender: 'user',
      content: input.trim(),
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };

    const asstId = `asst-${Date.now()}`;
    const asstMsg: Message = {
      id: asstId,
      sender: 'assistant',
      content: '',
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      isStreaming: true,
    };

    setMessages((prev) => [...prev, userMsg, asstMsg]);
    setInput('');
    setIsSending(true);

    try {
      await sendChatMessage(input.trim(), selectedDocIds, (chunk) => {
        setMessages((prev) =>
          prev.map((m) => (m.id === asstId ? { ...m, content: m.content + chunk } : m))
        );
      });
    } catch {
      setMessages((prev) =>
        prev.map((m) =>
          m.id === asstId
            ? {
                ...m,
                content: '⚠️ RAG AI service not yet connected. This will be live in Phase 2 when the FastAPI AI service is integrated.',
                isStreaming: false,
              }
            : m
        )
      );
    } finally {
      setMessages((prev) =>
        prev.map((m) => (m.id === asstId ? { ...m, isStreaming: false } : m))
      );
      setIsSending(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="flex gap-6 h-[calc(100vh-5.5rem)]">
      {/* Sidebar: Document Manager */}
      <aside className="w-72 shrink-0 bg-slate-900 border border-slate-800 rounded-2xl p-4 flex flex-col overflow-hidden">
        <DocumentManager selectedDocIds={selectedDocIds} onToggleSelectDoc={handleToggleDoc} />
      </aside>

      {/* Chat Panel */}
      <div className="flex-1 flex flex-col bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden">
        {/* Header */}
        <div className="px-5 py-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center">
              <Bot className="w-4 h-4 text-indigo-400" />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-white">RAG Chat Console</h2>
              <p className="text-xs text-slate-500">
                {selectedDocIds.length > 0
                  ? `${selectedDocIds.length} document(s) in context`
                  : 'No context selected'}
              </p>
            </div>
          </div>
          {selectedDocIds.length > 0 && (
            <div className="flex items-center gap-1.5 px-2.5 py-1 bg-indigo-500/10 border border-indigo-500/20 rounded-full">
              <div className="w-1.5 h-1.5 bg-indigo-400 rounded-full animate-pulse" />
              <span className="text-xs text-indigo-300 font-medium">Context Active</span>
            </div>
          )}
        </div>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto p-5 space-y-5">
          {messages.length === 0 ? (
            <EmptyState />
          ) : (
            messages.map((msg) => <MessageBubble key={msg.id} msg={msg} />)
          )}
          <div ref={bottomRef} />
        </div>

        {/* Input */}
        <div className="p-4 border-t border-slate-800 bg-slate-900/60">
          <div className="flex gap-3 items-end bg-slate-950 border border-slate-700 rounded-xl px-4 py-3 focus-within:border-indigo-500 transition-colors">
            <textarea
              ref={textareaRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Ask a question about your documents... (Enter to send, Shift+Enter for newline)"
              rows={1}
              className="flex-1 bg-transparent text-sm text-slate-100 placeholder-slate-500 resize-none focus:outline-none max-h-32"
              style={{ lineHeight: '1.5' }}
              disabled={isSending}
            />
            <button
              onClick={handleSend}
              disabled={!input.trim() || isSending}
              className="shrink-0 w-8 h-8 flex items-center justify-center bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 disabled:cursor-not-allowed text-white rounded-lg transition-colors"
            >
              <Send className="w-4 h-4" />
            </button>
          </div>
          <p className="text-xs text-slate-600 mt-2 text-center">
            AI answers will cite sources from your selected documents
          </p>
        </div>
      </div>
    </div>
  );
};
