const MAX_NODES_PER_PLAN = 8

export function createPlanHistoryEntry({ goal, result }) {
  if (!result || typeof result !== 'object') return null

  const nodes = collectCompletionNodes(result)
  const title = result.planTitle || result.plan_title || firstLine(goal) || ''
  const summary = result.planSummary || result.plan_summary || ''

  if (!title.trim() || !nodes.length) return null

  const now = new Date().toISOString()

  return {
    id: createPlanId(title, now),
    title: title.trim(),
    goal: (goal || '').trim(),
    summary: summary.trim(),
    result,
    nodes,
    createdAt: now,
    updatedAt: now,
  }
}

export function upsertPlanHistory(history, entry) {
  if (!entry) return history

  return [entry, ...history.filter((item) => item.id !== entry.id)]
}

export function refreshPlanHistoryEntry({ current, incoming, feedback }) {
  if (!current) return incoming
  if (!incoming) return updatePlanProgressFromFeedback(current, feedback)

  const currentByLabel = new Map(
    current.nodes.map((node) => [normalizeText(node.label), node]),
  )
  const incomingWithProgress = incoming.nodes.map((node) => {
    const previous = currentByLabel.get(normalizeText(node.label))
    return previous
      ? {
          ...node,
          completed: previous.completed,
          completedAt: previous.completedAt,
        }
      : node
  })

  const merged = {
    ...current,
    title: incoming.title,
    goal: incoming.goal || current.goal,
    summary: incoming.summary,
    result: incoming.result || current.result,
    nodes: incomingWithProgress,
    updatedAt: new Date().toISOString(),
  }

  return updatePlanProgressFromFeedback(merged, feedback)
}

export function togglePlanNodeCompletion(plan, nodeId) {
  if (!plan || !nodeId) return plan

  let changed = false
  const nodes = plan.nodes.map((node) => {
    if (node.id !== nodeId) return node
    changed = true
    const completed = !node.completed
    return {
      ...node,
      completed,
      completedAt: completed ? new Date().toISOString() : null,
    }
  })

  if (!changed) return plan
  return { ...plan, nodes, updatedAt: new Date().toISOString() }
}

export function updatePlanProgressFromFeedback(plan, feedback) {
  if (!plan || !feedback?.trim()) return plan

  const feedbackText = normalizeText(feedback)
  let changed = false
  const nodes = plan.nodes.map((node) => {
    if (node.completed || !matchesNodeFeedback(node, feedbackText)) return node
    changed = true
    return { ...node, completed: true, completedAt: new Date().toISOString() }
  })

  if (!changed && hasCompletionSignal(feedbackText) && !hasNegativeSignal(feedbackText)) {
    const nextNodeIndex = nodes.findIndex((node) => !node.completed)
    if (nextNodeIndex >= 0) {
      changed = true
      nodes[nextNodeIndex] = {
        ...nodes[nextNodeIndex],
        completed: true,
        completedAt: new Date().toISOString(),
      }
    }
  }

  if (!changed) return plan
  return { ...plan, nodes, updatedAt: new Date().toISOString() }
}

export function getPlanProgress(plan) {
  const nodes = Array.isArray(plan?.nodes) ? plan.nodes : []
  if (!nodes.length) return 0

  const completed = nodes.filter((node) => node.completed).length
  return Math.round((completed / nodes.length) * 100)
}

export function getPlanDisplayResult(plan) {
  if (!plan) return null
  if (plan.result && typeof plan.result === 'object') return plan.result

  return {
    planTitle: plan.title,
    planSummary: plan.summary,
    phases: plan.nodes.map((node) => ({
      name: node.label,
      focus: node.detail,
    })),
    todayTasks: [],
    reviewQuestions: [],
  }
}

export function formatPlanDate(value) {
  if (!value) return ''
  try {
    return new Intl.DateTimeFormat('zh-CN', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(value))
  } catch {
    return ''
  }
}

function collectCompletionNodes(result) {
  const phases = Array.isArray(result.phases) ? result.phases : []
  const tasks = Array.isArray(result.todayTasks || result.today_tasks)
    ? result.todayTasks || result.today_tasks
    : []

  const phaseNodes = phases.map((phase) => ({
    label: phase.name || phase.title || '',
    detail: phase.focus || phase.description || phase.duration || '',
  }))

  const taskNodes = tasks.map((task) => ({
    label: task.title || task.name || '',
    detail: task.description || task.difficulty || '',
  }))

  return [...phaseNodes, ...taskNodes]
    .filter((node) => node.label.trim())
    .slice(0, MAX_NODES_PER_PLAN)
    .map((node, index) => ({
      id: `node-${index + 1}`,
      label: node.label.trim(),
      detail: String(node.detail || '').trim(),
      completed: false,
      completedAt: null,
    }))
}

function matchesNodeFeedback(node, feedbackText) {
  const terms = [node.label, node.detail]
    .flatMap((value) => [...tokenize(value), ...chinesePhrases(value)])
    .filter((term) => term.length >= 2)

  if (!terms.length) return false
  return splitFeedbackClauses(feedbackText).some((clause) => {
    if (!hasCompletionSignal(clause) || hasNegativeSignal(clause)) return false
    return terms.some((term) => clause.includes(term))
  })
}

function tokenize(value) {
  return normalizeText(value)
    .split(/[\s,，.。:：;；、/|()[\]{}"'“”‘’!?！？-]+/)
    .filter(Boolean)
}

function chinesePhrases(value) {
  const text = normalizeText(value).replace(/[^\u4e00-\u9fa5]/g, '')
  if (text.length < 2) return []

  const phrases = []
  for (let size = Math.min(6, text.length); size >= 2; size -= 1) {
    for (let index = 0; index <= text.length - size; index += 1) {
      phrases.push(text.slice(index, index + size))
    }
  }
  return phrases
}

function normalizeText(value) {
  return String(value || '').trim().toLowerCase()
}

function splitFeedbackClauses(value) {
  return normalizeText(value)
    .split(/[，,。.;；!！?？\n]+/)
    .map((clause) => clause.trim())
    .filter(Boolean)
}

function hasCompletionSignal(value) {
  return /完成|做完|搞定|结束|已做|done|finish|finished|complete|completed/.test(value)
}

function hasNegativeSignal(value) {
  return /没|未|还没|没有|not|pending|todo|later/.test(value)
}

function firstLine(value) {
  return String(value || '').split('\n').find((line) => line.trim()) || ''
}

function createPlanId(title, createdAt) {
  const slug = title
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 40)
  return `${slug || 'plan'}-${Date.parse(createdAt)}`
}
