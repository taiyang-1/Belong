import http from './http'

export function sendMessage({ message, profileContext = '', recentContext = '' }) {
  return http
    .post('/api/chat', {
      message,
      profileContext,
      recentContext,
    })
    .then((res) => res.data)
}
