<script setup>
import { ref } from 'vue'
import { ClipboardList } from '@lucide/vue'
import { organize } from '../api/organizeApi.js'
import { normalizeResult } from '../utils/normalizeResult.js'
import LoadingButton from '../components/common/LoadingButton.vue'
import StatusMessage from '../components/common/StatusMessage.vue'
import ResultSection from '../components/common/ResultSection.vue'
import EmptyState from '../components/common/EmptyState.vue'

const emit = defineEmits(['done'])

const content = ref('')
const userRequest = '帮我整理重点和待办'
const profileContext = '用户喜欢温和、具体、低压力、不要太鸡血的建议。'
const loading = ref(false)
const error = ref('')
const result = ref(null)

async function handleOrganize() {
  if (!content.value.trim() || loading.value) return

  error.value = ''
  result.value = null

  loading.value = true
  try {
    const data = await organize({
      content: content.value,
      contentType: 'text',
      userRequest,
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
  <div class="organize-view">
    <div class="view-header">
      <h1 class="view-title">Organize</h1>
      <p class="view-subtitle">Paste text, documents, or notes. Belong structures them for you.</p>
    </div>

    <div class="form-area">
      <div class="field">
        <label class="field-label">Content</label>
        <textarea
          v-model="content"
          class="field-textarea large"
          placeholder="Paste your text here..."
          rows="8"
          :disabled="loading"
        ></textarea>
      </div>

      <div class="form-actions">
        <LoadingButton
          :loading="loading"
          :disabled="!content.trim()"
          label="Organize"
          loading-label="Organizing..."
          @click="handleOrganize"
        />
      </div>

      <StatusMessage v-if="error" type="error" :message="error" />
    </div>

    <div v-if="result" class="result-area">
      <h2 v-if="result.title" class="result-title">{{ result.title }}</h2>

      <ResultSection v-if="result.answer" title="Answer">
        <p>{{ result.answer }}</p>
      </ResultSection>

      <ResultSection v-if="result.summary" title="Summary">
        <p>{{ result.summary }}</p>
      </ResultSection>

      <ResultSection v-if="result.keyPoints && result.keyPoints.length" title="Key Points">
        <ul class="list">
          <li v-for="(point, i) in result.keyPoints" :key="i">{{ point }}</li>
        </ul>
      </ResultSection>

      <ResultSection v-if="result.todos && result.todos.length" title="To Do">
        <ul class="list check">
          <li v-for="(todo, i) in result.todos" :key="i">{{ todo }}</li>
        </ul>
      </ResultSection>

      <EmptyState
        v-if="!result.answer && !result.summary && !result.keyPoints && !result.todos"
        :icon="ClipboardList"
        title="Nothing to show"
        description="Dify returned a result without structured content."
      />
    </div>
  </div>
</template>

<style scoped>
.organize-view {
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

.field-input,
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

.field-input:focus,
.field-textarea:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.field-textarea.large {
  min-height: 220px;
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

.list {
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.list li {
  font-size: 14px;
  color: #4b5563;
  line-height: 1.6;
}

.list.check li::marker {
  content: "□ ";
}

@media (max-width: 920px) {
  .organize-view {
    padding: 24px;
  }
}
</style>
