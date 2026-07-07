/**
 * Normalize Dify result: ensure camelCase keys match what the frontend expects.
 * The backend returns snake_case keys (plan_title, key_points, etc.) from Dify JSON.
 */
export function normalizeResult(result) {
  if (!result || typeof result !== 'object') return result

  const normalized = { ...result }

  const keyMap = {
    plan_title: 'planTitle',
    plan_summary: 'planSummary',
    today_tasks: 'todayTasks',
    review_questions: 'reviewQuestions',
    key_points: 'keyPoints',
    suggested_actions: 'suggestedActions',
    memory_event: 'memoryEvent',
    content_type: 'contentType',
    user_request: 'userRequest',
    profile_context: 'profileContext',
    plan_action: 'planAction',
    user_goal: 'userGoal',
    current_plan: 'currentPlan',
    progress_context: 'progressContext',
    memory_context: 'memoryContext',
    recent_context: 'recentContext',
    created_at: 'createdAt',
    updated_at: 'updatedAt',
    expires_at: 'expiresAt',
    expires_in_days: 'expiresInDays',
    user_id: 'userId',
    is_archived: 'isArchived',
    should_remember: 'shouldRemember',
  }

  for (const [snake, camel] of Object.entries(keyMap)) {
    if (snake in normalized && !(camel in normalized)) {
      normalized[camel] = normalized[snake]
    }
  }

  // Recursively normalize nested arrays/objects
  for (const key of Object.keys(normalized)) {
    if (Array.isArray(normalized[key])) {
      normalized[key] = normalized[key].map((item) =>
        typeof item === 'object' ? normalizeResult(item) : item,
      )
    } else if (normalized[key] && typeof normalized[key] === 'object') {
      normalized[key] = normalizeResult(normalized[key])
    }
  }

  return normalized
}
