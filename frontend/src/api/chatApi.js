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
