export interface ChatMessageDTO {
    role: 'user' | 'assistant'
    content: string
}

export interface DrawChatRequest {
    messages: ChatMessageDTO[]
    xml?: string
    previousXml?: string
    sessionId?: string
    minimalStyle?: boolean
    customSystemMessage?: string
}

export interface SseEvent {
    type: 'text-delta' | 'tool-call' | 'done' | 'error' | 'heartbeat'
    name?: string
    content?: any
    arguments?: { xml: string }
    delta?: string
}
