<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Calendar, CheckCircle2, Clock, HelpCircle, PlusCircle, Target, X } from '@lucide/vue'
import { createOrUpdatePlan } from '../api/planApi.js'
import { getPlanActionCopy } from '../utils/planActionCopy.js'
import { normalizeResult } from '../utils/normalizeResult.js'
import {
  createPlanHistoryEntry,
  formatPlanDate,
  getPlanDisplayResult,
  getPlanProgress,
  refreshPlanHistoryEntry,
  togglePlanNodeCompletion,
  upsertPlanHistory,
  updatePlanProgressFromFeedback,
} from '../utils/planHistory.js'
import LoadingButton from '../components/common/LoadingButton.vue'
import StatusMessage from '../components/common/StatusMessage.vue'
import ResultSection from '../components/common/ResultSection.vue'
import EmptyState from '../components/common/EmptyState.vue'

const emit = defineEmits(['done'])
const PLAN_HISTORY_KEY = 'belong.planHistory.v1'

const profileContext = '用户喜欢温和、具体、低压力、不要太鸡血的建议。'
const actionOptions = [
  { value: 'adjust', label: '调整' },
  { value: 'review', label: '复盘' },
]

const createGoal = ref('')
const detailInput = ref('')
const currentPlan = ref('')
const progressContext = ref('')
const planAction = ref('adjust')
const createLoading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const result = ref(null)
const planHistory = ref([])
const selectedPlanId = ref('')
const showCreateModal = ref(false)
const liveDetailFeedback = ref('')
const liveProgressContext = ref('')
let detailFeedbackTimer = null
let progressFeedbackTimer = null

const selectedPlan = computed(() =>
  planHistory.value.find((plan) => plan.id === selectedPlanId.value) || null,
)
const activeActionCopy = computed(() => getPlanActionCopy(planAction.value))
const completedPlansCount = computed(() =>
  planHistory.value.filter((plan) => getPlanProgress(plan) === 100).length,
)
const averageProgress = computed(() => {
  if (!planHistory.value.length) return 0
  const total = planHistory.value.reduce((sum, plan) => sum + getPlanProgress(plan), 0)
  return Math.round(total / planHistory.value.length)
})

onMounted(() => {
  planHistory.value = loadPlanHistory()
  if (planHistory.value[0]) {
    selectPlan(planHistory.value[0])
  }
})

watch(detailInput, (value) => {
  queueProgressUpdate(value, 'detail')
})

watch(progressContext, (value) => {
  queueProgressUpdate(value, 'progress')
})

async function handleCreatePlan() {
  const goal = createGoal.value.trim()
  if (!goal || createLoading.value) return

  error.value = ''
  createLoading.value = true
  try {
    const data = await createOrUpdatePlan({
      planAction: 'create',
      userGoal: goal,
      currentPlan: '',
      progressContext: '',
      profileContext,
    })
    const planResult = normalizeResult(data.result || {})
    const newEntry = createPlanHistoryEntry({ goal, result: planResult })
    const nextHistory = upsertPlanHistory(planHistory.value, newEntry)

    planHistory.value = nextHistory
    savePlanHistory(nextHistory)
    if (newEntry) selectPlan(newEntry)
    createGoal.value = ''
    showCreateModal.value = false
    emit('done')
  } catch (e) {
    error.value = e.message || 'Belong 暂时没有回应，请稍后再试。'
  } finally {
    createLoading.value = false
  }
}

async function handlePlanAction() {
  const request = detailInput.value.trim()
  if (!selectedPlan.value || !request || actionLoading.value) return

  applyProgressFeedback(request)
  applyProgressFeedback(progressContext.value)

  error.value = ''
  actionLoading.value = true
  try {
    const data = await createOrUpdatePlan({
      planAction: planAction.value,
      userGoal: request,
      currentPlan: currentPlan.value,
      progressContext: progressContext.value,
      profileContext,
    })
    const planResult = normalizeResult(data.result || {})
    const incoming = createPlanHistoryEntry({
      goal: selectedPlan.value.goal || selectedPlan.value.title,
      result: planResult,
    })
    const refreshed = refreshPlanHistoryEntry({
      current: selectedPlan.value,
      incoming,
      feedback: '',
    })
    replacePlanInHistory(refreshed)
    result.value = getPlanDisplayResult(refreshed)
    detailInput.value = ''
    progressContext.value = ''
    emit('done')
  } catch (e) {
    error.value = e.message || 'Belong 暂时没有回应，请稍后再试。'
  } finally {
    actionLoading.value = false
  }
}

function openCreateModal() {
  error.value = ''
  createGoal.value = ''
  showCreateModal.value = true
}

function closeCreateModal() {
  if (createLoading.value) return
  showCreateModal.value = false
}

function selectPlan(plan) {
  selectedPlanId.value = plan.id
  result.value = getPlanDisplayResult(plan)
  currentPlan.value = plan.summary || plan.nodes.map((node) => node.label).join('\n')
  detailInput.value = ''
  progressContext.value = ''
  liveDetailFeedback.value = ''
  liveProgressContext.value = ''
}

function handleNodeToggle(plan, nodeId) {
  const updatedPlan = togglePlanNodeCompletion(plan, nodeId)
  replacePlanInHistory(updatedPlan)
}

function replacePlanInHistory(updatedPlan) {
  if (!updatedPlan) return

  const nextHistory = planHistory.value.map((plan) =>
    plan.id === updatedPlan.id ? updatedPlan : plan,
  )
  planHistory.value = nextHistory
  selectedPlanId.value = updatedPlan.id
  savePlanHistory(nextHistory)
}

function queueProgressUpdate(value, source) {
  const text = value.trim()
  if (!selectedPlan.value || !text) return

  const timerName = source === 'detail' ? 'detail' : 'progress'
  if (timerName === 'detail') {
    clearTimeout(detailFeedbackTimer)
    detailFeedbackTimer = setTimeout(() => {
      if (text !== liveDetailFeedback.value) {
        liveDetailFeedback.value = text
        applyProgressFeedback(text)
      }
    }, 450)
  } else {
    clearTimeout(progressFeedbackTimer)
    progressFeedbackTimer = setTimeout(() => {
      if (text !== liveProgressContext.value) {
        liveProgressContext.value = text
        applyProgressFeedback(text)
      }
    }, 450)
  }
}

function applyProgressFeedback(feedback) {
  if (!selectedPlan.value || !feedback.trim()) return

  const updatedPlan = updatePlanProgressFromFeedback(selectedPlan.value, feedback)
  if (updatedPlan !== selectedPlan.value) {
    replacePlanInHistory(updatedPlan)
  }
}

function loadPlanHistory() {
  try {
    const parsed = JSON.parse(localStorage.getItem(PLAN_HISTORY_KEY) || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function savePlanHistory(history) {
  localStorage.setItem(PLAN_HISTORY_KEY, JSON.stringify(history))
}

const difficultyColor = (d) => {
  if (d === 'easy') return '#14b8a6'
  if (d === 'medium') return '#f59e0b'
  if (d === 'hard') return '#dc2626'
  return '#6b7280'
}
</script>

<template>
  <div class="plan-view">
    <header class="plan-header">
      <div>
        <h1 class="view-title">Plan</h1>
        <p class="view-subtitle">Manage every plan, track progress, and adjust the next step.</p>
      </div>
      <button type="button" class="create-button" @click="openCreateModal">
        <PlusCircle :size="17" :stroke-width="2" />
        新建计划
      </button>
    </header>

    <section class="plan-summary" aria-label="计划概览">
      <div class="summary-item">
        <span>全部计划</span>
        <strong>{{ planHistory.length }}</strong>
      </div>
      <div class="summary-item">
        <span>已完成</span>
        <strong>{{ completedPlansCount }}</strong>
      </div>
      <div class="summary-item">
        <span>平均进度</span>
        <strong>{{ averageProgress }}%</strong>
      </div>
    </section>

    <section class="plans-area" aria-labelledby="plans-title">
      <div class="section-heading">
        <div>
          <h2 id="plans-title">所有计划</h2>
          <p>点击计划查看完整内容，点击节点直接更新进度。</p>
        </div>
      </div>

      <div v-if="planHistory.length" class="plan-grid">
        <article
          v-for="plan in planHistory"
          :key="plan.id"
          class="plan-card"
          :class="{ active: selectedPlan?.id === plan.id }"
          role="button"
          tabindex="0"
          @click="selectPlan(plan)"
          @keydown.enter="selectPlan(plan)"
          @keydown.space.prevent="selectPlan(plan)"
        >
          <div class="plan-card-top">
            <div class="progress-pie" :style="{ '--progress': `${getPlanProgress(plan)}%` }">
              <span>{{ getPlanProgress(plan) }}%</span>
            </div>
            <div class="plan-card-title">
              <h3>{{ plan.title }}</h3>
              <small>{{ formatPlanDate(plan.updatedAt || plan.createdAt) }}</small>
            </div>
          </div>
          <p v-if="plan.summary" class="plan-card-summary">{{ plan.summary }}</p>
          <div class="node-meta">
            <span>{{ plan.nodes.filter((node) => node.completed).length }}/{{ plan.nodes.length }} 节点完成</span>
          </div>
          <ul class="node-list">
            <li v-for="node in plan.nodes.slice(0, 3)" :key="node.id" :class="{ done: node.completed }">
              <button
                type="button"
                class="node-toggle"
                :aria-pressed="node.completed"
                @click.stop="handleNodeToggle(plan, node.id)"
              >
                <CheckCircle2 :size="14" :stroke-width="1.8" />
                <span>{{ node.label }}</span>
              </button>
            </li>
          </ul>
        </article>
      </div>

      <div v-else class="empty-board">
        <EmptyState
          :icon="Calendar"
          title="还没有计划"
          description="从右上角新建一个计划，它会出现在这里。"
        />
        <button type="button" class="secondary-create-button" @click="openCreateModal">
          <PlusCircle :size="16" :stroke-width="2" />
          新建计划
        </button>
      </div>
    </section>

    <section v-if="selectedPlan && result" class="detail-area" aria-labelledby="plan-detail-title">
      <div class="detail-main">
        <div class="detail-heading">
          <div>
            <span class="detail-kicker">计划详情</span>
            <h2 id="plan-detail-title">{{ result.planTitle || result.plan_title || selectedPlan.title }}</h2>
          </div>
          <div class="detail-progress">
            <div class="progress-pie small" :style="{ '--progress': `${getPlanProgress(selectedPlan)}%` }">
              <span>{{ getPlanProgress(selectedPlan) }}%</span>
            </div>
          </div>
        </div>

        <ResultSection v-if="result.planSummary || result.plan_summary" title="Summary">
          <p>{{ result.planSummary || result.plan_summary }}</p>
        </ResultSection>

        <ResultSection v-if="result.phases && result.phases.length" title="Phases">
          <div class="phases">
            <div v-for="(phase, i) in result.phases" :key="i" class="phase-card">
              <div class="phase-header">
                <Target :size="14" :stroke-width="1.5" />
                <span class="phase-name">{{ phase.name }}</span>
              </div>
              <p v-if="phase.focus" class="phase-focus">{{ phase.focus }}</p>
              <span v-if="phase.duration" class="phase-duration">
                <Clock :size="12" :stroke-width="1.5" />
                {{ phase.duration }}
              </span>
            </div>
          </div>
        </ResultSection>

        <ResultSection v-if="result.todayTasks && result.todayTasks.length" title="Today's Tasks">
          <div class="tasks">
            <div v-for="(task, i) in result.todayTasks" :key="i" class="task-row">
              <span class="task-dot" :style="{ background: difficultyColor(task.difficulty) }"></span>
              <span class="task-title">{{ task.title }}</span>
              <span v-if="task.estimatedMinutes" class="task-time">{{ task.estimatedMinutes }} min</span>
            </div>
          </div>
        </ResultSection>

        <ResultSection v-if="result.reviewQuestions && result.reviewQuestions.length" title="Review Questions">
          <ul class="list">
            <li v-for="(q, i) in result.reviewQuestions" :key="i">
              <HelpCircle :size="13" :stroke-width="1.5" class="q-icon" />
              {{ q }}
            </li>
          </ul>
        </ResultSection>
      </div>

      <aside class="detail-panel">
        <h3>更新这个计划</h3>
        <div class="segmented">
          <button
            v-for="opt in actionOptions"
            :key="opt.value"
            class="seg-item"
            :class="{ active: planAction === opt.value }"
            @click="planAction = opt.value"
          >{{ opt.label }}</button>
        </div>
        <p class="action-hint">{{ activeActionCopy.hint }}</p>

        <label class="field-label">{{ activeActionCopy.label }}</label>
        <textarea
          v-model="detailInput"
          class="field-textarea"
          rows="4"
          :placeholder="activeActionCopy.placeholder"
          :disabled="actionLoading"
        ></textarea>

        <label class="field-label">补充进展</label>
        <textarea
          v-model="progressContext"
          class="field-textarea"
          rows="3"
          placeholder="比如：已经完成了入门教程，下一步想更轻一点。"
          :disabled="actionLoading"
        ></textarea>

        <LoadingButton
          :loading="actionLoading"
          :disabled="!detailInput.trim()"
          :label="activeActionCopy.submitLabel"
          :loading-label="activeActionCopy.loadingLabel"
          @click="handlePlanAction"
        />
      </aside>
    </section>

    <StatusMessage v-if="error" type="error" :message="error" />

    <div v-if="showCreateModal" class="modal-backdrop" @click.self="closeCreateModal">
      <section class="create-modal" aria-labelledby="create-plan-title">
        <div class="modal-header">
          <div>
            <h2 id="create-plan-title">新建计划</h2>
            <p>只需要描述目标，生成后会自动加入计划列表。</p>
          </div>
          <button type="button" class="icon-button" aria-label="关闭" @click="closeCreateModal">
            <X :size="18" :stroke-width="2" />
          </button>
        </div>

        <label class="field-label">Your Goal</label>
        <textarea
          v-model="createGoal"
          class="field-textarea large"
          rows="5"
          placeholder="Describe what you want to plan..."
          :disabled="createLoading"
        ></textarea>

        <div class="modal-actions">
          <button type="button" class="ghost-button" :disabled="createLoading" @click="closeCreateModal">
            取消
          </button>
          <LoadingButton
            :loading="createLoading"
            :disabled="!createGoal.trim()"
            label="生成计划"
            loading-label="Planning..."
            @click="handleCreatePlan"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.plan-view {
  max-width: 1260px;
  margin: 0 auto;
  padding: 28px 36px 40px;
}

.plan-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 22px;
}

.view-title {
  max-width: 720px;
  font-size: 30px;
  font-weight: 800;
  line-height: 1.1;
  color: #111827;
  margin-bottom: 8px;
}

.view-subtitle {
  max-width: 720px;
  font-size: 15px;
  line-height: 1.65;
  color: #718096;
}

.create-button,
.secondary-create-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 15px;
  border-radius: 12px;
  background: #132033;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
}

.secondary-create-button {
  margin-top: 14px;
}

.plan-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.summary-item {
  padding: 18px;
  background: #fff;
  border: 1px solid #e1e7ef;
  border-radius: 16px;
}

.summary-item span {
  display: block;
  font-size: 12px;
  color: #7b8798;
  margin-bottom: 4px;
}

.summary-item strong {
  font-size: 24px;
  line-height: 1;
  color: #111827;
}

.plans-area,
.detail-area {
  background: #fff;
  border: 1px solid #e1e7ef;
  border-radius: 18px;
  padding: 24px;
  margin-bottom: 24px;
}

.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
}

.section-heading h2 {
  font-size: 19px;
  font-weight: 800;
  color: #111827;
}

.section-heading p {
  font-size: 13px;
  color: #7b8798;
}

.plan-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.plan-card {
  min-height: 220px;
  padding: 16px;
  border: 1px solid #e1e7ef;
  border-radius: 14px;
  background: #fbfcfe;
  text-align: left;
  transition: border-color 0.15s, background 0.15s, transform 0.15s;
}

.plan-card:hover,
.plan-card.active {
  border-color: #9bb7f4;
  background: #f8fbff;
}

.plan-card:hover {
  transform: translateY(-1px);
}

.plan-card-top {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 13px;
  align-items: center;
  margin-bottom: 12px;
}

.progress-pie {
  --progress: 0%;
  width: 54px;
  height: 54px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: conic-gradient(#2563eb var(--progress), #e7edf6 0);
  position: relative;
  flex-shrink: 0;
}

.progress-pie.small {
  width: 58px;
  height: 58px;
}

.progress-pie::after {
  content: "";
  position: absolute;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #fff;
}

.progress-pie.small::after {
  width: 42px;
  height: 42px;
}

.progress-pie span {
  position: relative;
  z-index: 1;
  font-size: 12px;
  font-weight: 800;
  color: #132033;
}

.plan-card-title h3 {
  font-size: 15px;
  font-weight: 800;
  line-height: 1.35;
  color: #111827;
  margin-bottom: 4px;
}

.plan-card-title small {
  color: #9aa4b2;
  font-size: 11px;
}

.plan-card-summary {
  font-size: 13px;
  color: #667085;
  line-height: 1.55;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.node-meta {
  padding-top: 2px;
  margin-bottom: 9px;
  font-size: 12px;
  color: #2563eb;
  font-weight: 700;
}

.node-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.node-list li {
  color: #667085;
  font-size: 12px;
  line-height: 1.4;
}

.node-toggle {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  width: 100%;
  text-align: left;
  color: inherit;
}

.node-toggle:hover {
  color: #2563eb;
}

.node-toggle svg {
  margin-top: 1px;
  color: #c0c9d7;
  flex-shrink: 0;
}

.node-list li.done {
  color: #1f6f5f;
}

.node-list li.done .node-toggle svg {
  color: #14b8a6;
}

.empty-board {
  display: grid;
  place-items: center;
  min-height: 280px;
  text-align: center;
}

.detail-area {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 24px;
  align-items: start;
}

.detail-main,
.detail-panel {
  min-width: 0;
}

.detail-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}

.detail-kicker {
  display: block;
  color: #7b8798;
  font-size: 12px;
  font-weight: 800;
  margin-bottom: 4px;
  text-transform: uppercase;
}

.detail-heading h2 {
  font-size: 22px;
  color: #111827;
  line-height: 1.35;
}

.detail-panel {
  position: sticky;
  top: 24px;
  padding: 18px;
  border: 1px solid #e1e7ef;
  border-radius: 14px;
  background: #fbfcfe;
}

.detail-panel h3 {
  font-size: 16px;
  color: #111827;
  margin-bottom: 12px;
}

.segmented {
  display: inline-flex;
  padding: 4px;
  border: 1px solid #e1e7ef;
  border-radius: 999px;
  overflow: hidden;
  background: #ffffff;
  margin-bottom: 8px;
}

.seg-item {
  padding: 8px 16px;
  border-radius: 999px;
  font-size: 13px;
  color: #667085;
  transition: background 0.15s, color 0.15s;
}

.seg-item:hover {
  background: #f9fafb;
}

.seg-item.active {
  background: #132033;
  color: #fff;
}

.action-hint {
  margin-bottom: 14px;
  font-size: 12px;
  color: #7b8798;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 750;
  color: #667085;
  margin: 12px 0 8px;
}

.field-textarea {
  width: 100%;
  padding: 13px 15px;
  border: 1px solid #dfe5ee;
  border-radius: 12px;
  font-size: 15px;
  outline: none;
  transition: border-color 0.15s;
  line-height: 1.5;
}

.field-textarea:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.field-textarea.large {
  min-height: 160px;
}

.phases {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.phase-card {
  padding: 14px 16px;
  border: 1px solid #e1e7ef;
  border-radius: 12px;
}

.phase-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.phase-name {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.phase-focus {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
}

.phase-duration {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #9ca3af;
}

.tasks {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 12px;
  background: #f7f9fc;
}

.task-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.task-title {
  font-size: 14px;
  color: #4b5563;
  flex: 1;
}

.task-time {
  font-size: 12px;
  color: #9ca3af;
  white-space: nowrap;
}

.list {
  padding-left: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  list-style: none;
}

.list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 14px;
  color: #4b5563;
  line-height: 1.6;
}

.q-icon {
  margin-top: 3px;
  color: #9ca3af;
  flex-shrink: 0;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(15, 23, 42, 0.22);
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  padding: 72px 48px;
}

.create-modal {
  width: min(460px, calc(100vw - 48px));
  background: #fff;
  border: 1px solid #e1e7ef;
  border-radius: 18px;
  padding: 22px;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.18);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.modal-header h2 {
  font-size: 20px;
  color: #111827;
  margin-bottom: 4px;
}

.modal-header p {
  color: #7b8798;
  font-size: 13px;
}

.icon-button {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: #667085;
  background: #f7f9fc;
}

.ghost-button {
  height: 42px;
  padding: 0 15px;
  border-radius: 12px;
  border: 1px solid #dfe5ee;
  color: #667085;
  background: #fff;
  font-weight: 700;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

@media (max-width: 980px) {
  .plan-view {
    padding: 24px;
  }

  .plan-header,
  .detail-heading {
    flex-direction: column;
  }

  .plan-summary {
    grid-template-columns: 1fr;
  }

  .detail-area {
    grid-template-columns: 1fr;
  }

  .detail-panel {
    position: static;
  }

  .modal-backdrop {
    padding: 24px;
  }
}
</style>
