import http from './http'

export function createOrUpdatePlan({
  planAction = 'create',
  userGoal,
  currentPlan = '',
  progressContext = '',
  profileContext,
}) {
  return http
    .post('/api/plan', {
      planAction,
      userGoal,
      currentPlan,
      progressContext,
      profileContext,
    })
    .then((res) => res.data)
}
