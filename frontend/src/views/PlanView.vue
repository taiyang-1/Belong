<script setup>
import { ref } from 'vue'
import { Calendar, Clock, Target, HelpCircle } from '@lucide/vue'
import { createOrUpdatePlan } from '../api/planApi.js'
import { normalizeResult } from '../utils/normalizeResult.js'
import LoadingButton from '../components/common/LoadingButton.vue'
import StatusMessage from '../components/common/StatusMessage.vue'
import ResultSection from '../components/common/ResultSection.vue'
import EmptyState from '../components/common/EmptyState.vue'

const emit = defineEmits(['done'])

const planAction = ref('create')
const userGoal = ref('')
const currentPlan = ref('')
const progressContext = ref('')
const profileContext = '用户喜欢温和、具体、低压力、不要太鸡血的建议。'
const loading = ref(false)
const error = ref('')
const result = ref(null)

const actionOptions = [
  { value: 'create', label: 'Create' },
  { value: 'adjust', label: 'Adjust' },
  { value: 'review', label: 'Review' },
]

const difficultyColor = (d) => {
  if (d === 'easy') return '#14b8a6'
  if (d === 'medium') return '#f59e0b'
  if (d === 'hard') return '#dc2626'
  return '#6b7280'
}

async function handlePlan() {
  if (!userGoal.value.trim() || loading.value) return

  error.value = ''
  result.value = null

  loading.value = true
  try {
    const data = await createOrUpdatePlan({
      planAction: planAction.value,
      userGoal: userGoal.value,
      currentPlan: currentPlan.value,
      progressContext: progressContext.value,
      profileContext,
    })
    result.value = normalizeResult(data.result || {})
    emit('done')
  } catch (e) {
    error.value = e.message || 'Belong 暂时没有回应，请稍后再试。'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="plan-view">
    <div class="view-header">
      <h1 class="view-title">Plan</h1>
      <p class="view-subtitle">Create, adjust, or review plans that fit your real situation.</p>
    </div>

    <div class="form-area">
      <div class="field">
        <label class="field-label">Action</label>
        <div class="segmented">
          <button
            v-for="opt in actionOptions"
            :key="opt.value"
            class="seg-item"
            :class="{ active: planAction === opt.value }"
            @click="planAction = opt.value"
          >{{ opt.label }}</button>
        </div>
      </div>

      <div class="field">
        <label class="field-label">Your Goal</label>
        <textarea
          v-model="userGoal"
          class="field-textarea large"
          placeholder="Describe what you want to plan..."
          rows="5"
          :disabled="loading"
        ></textarea>
      </div>

      <details class="advanced">
        <summary class="advanced-toggle">Current Plan & Progress</summary>
        <div class="field">
          <label class="field-label">Current Plan</label>
          <textarea
            v-model="currentPlan"
            class="field-textarea"
            rows="3"
            :disabled="loading"
            placeholder="Describe your existing plan if any..."
          ></textarea>
        </div>
        <div class="field">
          <label class="field-label">Progress Context</label>
          <textarea
            v-model="progressContext"
            class="field-textarea"
            rows="2"
            :disabled="loading"
            placeholder="What progress have you made so far?"
          ></textarea>
        </div>
      </details>

      <div class="form-actions">
        <LoadingButton
          :loading="loading"
          :disabled="!userGoal.trim()"
          label="Plan"
          loading-label="Planning..."
          @click="handlePlan"
        />
      </div>

      <StatusMessage v-if="error" type="error" :message="error" />
    </div>

    <div v-if="result" class="result-area">
      <h2 v-if="result.planTitle || result.plan_title" class="result-title">
        {{ result.planTitle || result.plan_title }}
      </h2>

      <ResultSection v-if="result.planSummary || result.plan_summary" title="Summary">
        <p>{{ result.planSummary || result.plan_summary }}</p>
      </ResultSection>

      <!-- Phases -->
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

      <!-- Today's Tasks -->
      <ResultSection v-if="result.todayTasks && result.todayTasks.length" title="Today's Tasks">
        <div class="tasks">
          <div v-for="(task, i) in result.todayTasks" :key="i" class="task-row">
            <span class="task-dot" :style="{ background: difficultyColor(task.difficulty) }"></span>
            <span class="task-title">{{ task.title }}</span>
            <span v-if="task.estimatedMinutes" class="task-time">{{ task.estimatedMinutes }} min</span>
          </div>
        </div>
      </ResultSection>

      <!-- Review Questions -->
      <ResultSection v-if="result.reviewQuestions && result.reviewQuestions.length" title="Review Questions">
        <ul class="list">
          <li v-for="(q, i) in result.reviewQuestions" :key="i">
            <HelpCircle :size="13" :stroke-width="1.5" class="q-icon" />
            {{ q }}
          </li>
        </ul>
      </ResultSection>

      <EmptyState
        v-if="!result.planTitle && !result.planSummary && (!result.phases || !result.phases.length) && (!result.todayTasks || !result.todayTasks.length)"
        :icon="Calendar"
        title="No structured plan returned"
        description="Try rephrasing your goal."
      />
    </div>
  </div>
</template>

<style scoped>
.plan-view {
  max-width: 1040px;
  margin: 0 auto;
  padding: 28px 36px 40px;
}

.view-header {
  padding-bottom: 28px;
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

.form-area {
  background: #fff;
  border: 1px solid #e1e7ef;
  border-radius: 18px;
  padding: 28px;
  margin-bottom: 28px;
}

.field {
  margin-bottom: 18px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 750;
  color: #667085;
  margin-bottom: 8px;
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
  min-height: 150px;
}

.segmented {
  display: inline-flex;
  padding: 4px;
  border: 1px solid #e1e7ef;
  border-radius: 999px;
  overflow: hidden;
  background: #ffffff;
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

.advanced {
  margin-bottom: 18px;
}

.advanced-toggle {
  font-size: 12px;
  color: #9ca3af;
  cursor: pointer;
  user-select: none;
  margin-bottom: 8px;
}

.form-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.result-area {
  background: #fff;
  border: 1px solid #e1e7ef;
  border-radius: 18px;
  padding: 28px;
}

.result-title {
  font-size: 20px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 20px;
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

@media (max-width: 920px) {
  .plan-view {
    padding: 24px;
  }
}
</style>
