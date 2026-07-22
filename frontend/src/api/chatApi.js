import http from './http'

export function sendMessage({ conversationId = '', message, profileContext = '', recentContext = '' }) {
  return http
    .post('/api/chat', {
      conversationId,
      message,
      profileContext,
      recentContext,
    })
    .then((res) => res.data)
}

export function fetchChatMessages(limit = 20) {
  return http.get('/api/chat/messages', { params: { limit } }).then((res) => res.data)
}

export function fetchChatConversations(limit = 20) {
  return http.get('/api/chat/conversations', { params: { limit } }).then((res) => res.data)
}

export function fetchConversationMessages(conversationId) {
  return http.get(`/api/chat/conversations/${conversationId}/messages`).then((res) => res.data)
}

export function deleteChatConversation(conversationId) {
  return http.delete(`/api/chat/conversations/${conversationId}`)
}

export async function streamMessage(
  { conversationId = '', message, profileContext = '', recentContext = '' },
  { onMessage, onDone, onError },
) {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      conversationId,
      message,
      profileContext,
      recentContext,
    }),
  })

  if (!response.ok || !response.body) {
    onError?.(`Request failed with status ${response.status}`)
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const events = buffer.split('\n\n')
    buffer = events.pop() || ''

    for (const rawEvent of events) {
      await handleStreamEvent(rawEvent, { onMessage, onDone, onError })
    }
  }

  if (buffer.trim()) {
    await handleStreamEvent(buffer, { onMessage, onDone, onError })
  }
}

async function handleStreamEvent(rawEvent, handlers) {
  const lines = rawEvent.split('\n')
  const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message'
  const data = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')

  if (event === 'message') {
    await handlers.onMessage?.(data)
  } else if (event === 'done') {
    await handlers.onDone?.(JSON.parse(data))
  } else if (event === 'error') {
    await handlers.onError?.(data)
  }
}
