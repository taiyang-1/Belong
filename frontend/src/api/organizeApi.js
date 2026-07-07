import http from './http'

export function organize({ content, contentType = 'text', userRequest, profileContext }) {
  return http
    .post('/api/organize', {
      content,
      contentType,
      userRequest,
      profileContext,
    })
    .then((res) => res.data)
}
