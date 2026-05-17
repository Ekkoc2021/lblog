// 会话列表项
export interface ChatSessionVO {
  id: number;
  title: string | null;
  agentType: string;
  modelName: string;
  messageCount: number;
  previewText: string;
  createdAt: string;
  updatedAt: string;
}

// 单条消息（展示用）
export interface ChatMessageVO {
  id: number;
  sessionId: number;
  role: 'user' | 'assistant' | 'tool';
  content: string;
  reasoningContent?: string;
  toolCalls?: ToolCallVO[];
  msgIndex: number;
  createdAt: string;
}

export interface ToolCallVO {
  id: string;
  name: string;
  arguments: Record<string, unknown>;
}
