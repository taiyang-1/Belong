<script setup>
import { ref, nextTick } from 'vue'
import { Send, MessageSquare } from '@lucide/vue'
import { sendMessage } from '../api/chatApi.js'
import { normalizeResult } from '../utils/normalizeResult.js'
import StatusMessage from '../components/common/StatusMessage.vue'

const emit = defineEmits(['done'])

const messageInput = ref('')
const loading = ref(false)
const error = ref('')
const messages = ref([])

const profileContext = '用户喜欢温和、具体、低压力、不要太鸡血的建议。'

async function handleSend() {
  const text = messageInput.value.trim()
  if (!text || loading.value) return

  error.value = ''

  messages.value.push({ role: 'user', content: text, id: Date.now() })
  messageInput.value = ''

  loading.value = true
  try {
    const data = await sendMessage({
      message: text,
      profileContext,
    })
    const result = normalizeResult(data.result || {})
    messages.value.push({
      role: 'assistant',
      content: result.reply || '',
      suggestedActions: result.suggestedActions || [],
      id: Date.now() + 1,
    })
    emit('done')
  } catch (e) {
    error.value = e.message || 'Belong 暂时没有回应，请稍后再试。'
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

function scrollToBottom() {
  const el = document.querySelector('.chat-messages')
  if (el) el.scrollTop = el.scrollHeight
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}
</script>

<template>
  <div class="chat-view">
    <header class="view-header">
      <div>
        <div class="status-pill">
          <span></span>
          Belong is ready
        </div>
        <h1 class="view-title">Talk through the problem.</h1>
        <p class="view-subtitle">
          Belong gives you a calm place to think out loud, sort the next step, or write something less awkward.
        </p>
      </div>
      <div class="today-card">
        <span>Today</span>
        <strong>Low pressure</strong>
      </div>
    </header>

    <section v-if="!messages.length" class="empty-board">
      <div class="hero-panel">
        <div class="panel-icon">
          <MessageSquare :size="22" :stroke-width="1.6" />
        </div>
        <h2>Start with what feels messy.</h2>
        <p>
          No need to explain it perfectly. Tell Belong what is on your mind, and it will help make the next move smaller.
        </p>
        <div class="starter-list">
          <button type="button" @click="messageInput = '我今天有点烦，不知道从哪里开始。帮我理一下。'">
            <span></span>
            I feel stuck and need one small next step.
          </button>
          <button type="button" @click="messageInput = '帮我写一句自然一点的消息，不要太尴尬。'">
            <span></span>
            Help me draft a natural message.
          </button>
          <button type="button" @click="messageInput = '我计划太多了，帮我降压拆成今天能做的。'">
            <span></span>
            Turn a heavy plan into one tiny action.
          </button>
        </div>
      </div>
      <div class="side-panel">
        <h2>Why this works</h2>
        <p>
          The page stays structured like a tool, but the interaction still feels like talking to someone who understands your situation.
        </p>
      </div>
    </section>

    <div v-else class="chat-messages">

      <div
        v-for="msg in messages"
        :key="msg.id"
        class="message"
        :class="msg.role"
      >
        <div class="msg-content">{{ msg.content }}</div>
        <div v-if="msg.suggestedActions && msg.suggestedActions.length" class="suggested-actions">
          <span
            v-for="(action, i) in msg.suggestedActions"
            :key="i"
            class="action-chip"
          >{{ action }}</span>
        </div>
      </div>

      <div v-if="loading" class="message assistant">
        <div class="typing-indicator">
          <span></span><span></span><span></span>
        </div>
      </div>
    </div>

    <StatusMessage v-if="error" type="error" :message="error" class="chat-error" />

    <div class="chat-composer">
      <textarea
        v-model="messageInput"
        class="composer-input"
        placeholder="Say something..."
        rows="2"
        :disabled="loading"
        @keydown="handleKeydown"
      ></textarea>
      <button
        class="send-button"
        type="button"
        :disabled="!messageInput.trim() || loading"
        @click="handleSend"
      >
        <Send :size="22" :stroke-width="2" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 1040px;
  margin: 0 auto;
  padding: 28px 36px;
}

.view-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 28px;
  align-items: start;
  padding-bottom: 28px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  padding: 6px 11px;
  border: 1px solid #e1e7ef;
  border-radius: 999px;
  color: #6f7d92;
  font-size: 13px;
}

.status-pill span {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #14b8a6;
}

.view-title {
  max-width: 650px;
  margin-top: 12px;
  color: #111827;
  font-size: 30px;
  font-weight: 800;
  line-height: 1.1;
  letter-spacing: 0;
}

.view-subtitle {
  max-width: 680px;
  margin-top: 8px;
  color: #718096;
  font-size: 15px;
  line-height: 1.65;
}

.today-card {
  padding: 16px;
  border: 1px solid #e1e7ef;
  border-radius: 16px;
  background: #fbfcfe;
}

.today-card span {
  display: block;
  color: #718096;
  font-size: 13px;
  font-weight: 700;
}

.today-card strong {
  display: block;
  margin-top: 4px;
  color: #111827;
  font-size: 22px;
  line-height: 1.2;
}

.empty-board {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(280px, 0.9fr);
  gap: 18px;
  align-content: center;
  flex: 1;
  padding: 24px 0;
}

.hero-panel,
.side-panel {
  border: 1px solid #e1e7ef;
  border-radius: 18px;
  background: #ffffff;
}

.hero-panel {
  min-height: 340px;
  padding: 28px;
}

.side-panel {
  padding: 28px;
}

.panel-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: #eef4ff;
  color: #2563eb;
  margin-bottom: 18px;
}

.hero-panel h2,
.side-panel h2 {
  color: #111827;
  font-size: 24px;
  font-weight: 800;
  line-height: 1.2;
}

.hero-panel p,
.side-panel p {
  margin-top: 10px;
  color: #718096;
  font-size: 15px;
  line-height: 1.75;
}

.starter-list {
  display: grid;
  gap: 12px;
  margin-top: 28px;
}

.starter-list button {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f7f9fc;
  color: #667085;
  font-size: 14px;
  text-align: left;
  transition: transform 0.16s ease, background 0.16s ease;
}

.starter-list button:hover {
  background: #f1f5fb;
  transform: translateY(-1px);
}

.starter-list span {
  width: 9px;
  height: 9px;
  border-radius: 999px;
  background: #14b8a6;
  flex-shrink: 0;
}

.chat-messages {
  flex: 0 1 auto;
  max-height: min(52vh, 520px);
  min-height: 0;
  overflow-y: auto;
  border: 1px solid #e1e7ef;
  border-radius: 18px;
  background: #ffffff;
  padding: 24px;
  margin: 10px 0 22px;
}

.message {
  margin-bottom: 16px;
  max-width: 85%;
}

.message.user {
  margin-left: auto;
}

.message.user .msg-content {
  background: #2563eb;
  color: #fff;
  border-radius: 14px 14px 4px 14px;
  padding: 10px 16px;
  font-size: 14px;
  line-height: 1.6;
}

.message.assistant .msg-content {
  background: #f7f9fc;
  color: #374151;
  border: 1px solid #eef2f7;
  border-radius: 14px 14px 14px 4px;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.7;
}

.suggested-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.action-chip {
  font-size: 12px;
  padding: 5px 12px;
  border-radius: 14px;
  background: #f1f5f9;
  color: #4b5563;
  border: 1px solid #e5e7eb;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #9ca3af;
  animation: bounce 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }
.typing-indicator span:nth-child(3) { animation-delay: 0s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.chat-error {
  margin: 0 0 12px;
}

.chat-composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 58px;
  align-items: flex-end;
  gap: 12px;
  padding-top: 18px;
  border-top: 1px solid #e1e7ef;
}

.composer-input {
  width: 100%;
  min-height: 58px;
  max-height: 140px;
  padding: 17px 18px;
  border-radius: 14px;
  border: 1px solid #dfe5ee;
  background: #fff;
  color: #111827;
  font-size: 15px;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
  line-height: 1.5;
  box-shadow: 0 10px 28px rgba(23, 32, 51, 0.05);
}

.composer-input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.send-button {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 14px;
  background: #2563eb;
  color: #ffffff;
  box-shadow: 0 12px 26px rgba(37, 99, 235, 0.22);
  transition: transform 0.15s ease, background 0.15s ease, box-shadow 0.15s ease;
}

.send-button:hover:not(:disabled) {
  transform: translateY(-1px);
  background: #1d4ed8;
  box-shadow: 0 16px 30px rgba(37, 99, 235, 0.28);
}

.send-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 920px) {
  .chat-view {
    padding: 24px;
  }

  .view-header,
  .empty-board {
    grid-template-columns: 1fr;
  }

  .chat-messages {
    max-height: 46vh;
  }
}
</style>
