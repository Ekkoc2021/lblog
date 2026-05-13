import type { DrawChatRequest, SseEvent } from '../types/draw'

function getToken(): string | null {
    return sessionStorage.getItem('lblog_access_token')
}

export function drawChatStream(
    request: DrawChatRequest,
    onEvent: (event: SseEvent) => void,
    onError: (error: Error) => void,
    onComplete: () => void,
): AbortController {
    const controller = new AbortController()
    const token = getToken()

    const headers: Record<string, string> = {
        'Content-Type': 'application/json; charset=utf-8',
    }
    if (token) {
        headers['Authorization'] = `Bearer ${token}`
    }

    let completed = false

    fetch('/api/v1/draw/chat', {
        method: 'POST',
        headers,
        body: JSON.stringify(request),
        signal: controller.signal,
    })
        .then(async (response) => {
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`)
            }

            const text = await response.text()
            const allLines = text.split('\n')

            for (const line of allLines) {
                const trimmed = line.trim()
                if (!trimmed || trimmed.startsWith(':')) continue
                if (trimmed.startsWith('data:')) {
                    try {
                        const raw = trimmed.startsWith('data: ') ? trimmed.slice(6) : trimmed.slice(5)
                        const data = JSON.parse(raw)
                        if (data.type === 'error') {
                            onError(new Error(data.content || 'Unknown error'))
                        } else if (data.type === 'done') {
                            completed = true
                            onComplete()
                        } else if (data.type === 'heartbeat') {
                            continue
                        } else {
                            onEvent(data as SseEvent)
                        }
                    } catch {
                    }
                }
            }

            if (!completed) {
                onComplete()
            }
        })
        .catch((err) => {
            if (err.name === 'AbortError') return
            onError(err instanceof Error ? err : new Error(String(err)))
            onComplete()
        })

    return controller
}
