<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { PlusCircle, Send, MessageSquare } from '@lucide/vue'
import {
  deleteChatConversation,
  fetchChatConversations,
  fetchChatMessages,
  fetchConversationMessages,
  sendMessage,
} from '../api/chatApi.js'
import { normalizeResult } from '../utils/normalizeResult.js'
import StatusMessage from '../components/common/StatusMessage.vue'

const emit = defineEmits(['done'])

const messageInput = ref('')
const loading = ref(false)
const historyLoading = ref(false)
const error = ref('')
const messages = ref([])
const conversations = ref([])
const conversationId = ref(createConversationId())
const composerRef = ref(null)

const profileContext = '用户喜欢温和、具体、低压力、不要太鸡血的建议。'

onMounted(() => {
  loadLatestMessages()
})

async function loadLatestMessages() {
  historyLoading.value = true
  error.value = ''
  try {
    const [conversationData, messageData] = await Promise.all([
      fetchChatConversations(20),
      fetchChatMessages(20),
    ])
    conversations.value = conversationData.map(toConversationSummary)
    if (conversations.value.length) {
      conversationId.value = conversations.value[0].conversationId
      const activeMessages = await fetchConversationMessages(conversationId.value)
      messages.value = activeMessages.map(toViewMessage)
    } else {
      messages.value = messageData.map(toViewMessage)
    }
    await nextTick()
    scrollToBottom()
  } catch (e) {
    error.value = e.message || '最近聊天记录读取失败。'
  } finally {
    historyLoading.value = false
  }
}

async function handleSend() {
  const text = messageInput.value.trim()
  if (!text || loading.value) return

  error.value = ''

  messages.value.push({
    role: 'user',
    content: text,
    id: `local-user-${Date.now()}`,
    conversationId: conversationId.value,
  })
  messageInput.value = ''
  await nextTick()
  resizeComposer()
  scrollToBottom()

  loading.value = true
  try {
    const data = await sendMessage({
      conversationId: conversationId.value,
      message: text,
      profileContext,
    })
    const result = normalizeResult(data.result || {})
    messages.value.push({
      role: 'assistant',
      content: result.reply || '',
      suggestedActions: result.suggestedActions || [],
      id: `local-assistant-${Date.now()}`,
      conversationId: conversationId.value,
    })
    await refreshConversations()
    emit('done')
  } catch (e) {
    error.value = e.message || 'Belong 暂时没有回应，请稍后再试。'
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

function startNewChat() {
  conversationId.value = createConversationId()
  messages.value = []
  error.value = ''
  messageInput.value = ''
  nextTick(resizeComposer)
}

async function openConversation(id) {
  if (!id || id === conversationId.value) return

  historyLoading.value = true
  error.value = ''
  try {
    conversationId.value = id
    const data = await fetchConversationMessages(id)
    messages.value = data.map(toViewMessage)
    await nextTick()
    scrollToBottom()
  } catch (e) {
    error.value = e.message || '历史会话读取失败。'
  } finally {
    historyLoading.value = false
  }
}

async function deleteConversation(conversation, event) {
  event.stopPropagation()
  if (!conversation?.conversationId || loading.value || historyLoading.value) return

  const confirmed = window.confirm(`确定删除「${conversation.title}」这段会话吗？删除后无法恢复。`)
  if (!confirmed) return

  historyLoading.value = true
  error.value = ''
  try {
    await deleteChatConversation(conversation.conversationId)
    conversations.value = conversations.value.filter(
      (item) => item.conversationId !== conversation.conversationId,
    )

    if (conversation.conversationId === conversationId.value) {
      if (conversations.value.length) {
        conversationId.value = conversations.value[0].conversationId
        const data = await fetchConversationMessages(conversationId.value)
        messages.value = data.map(toViewMessage)
      } else {
        startNewChat()
      }
    }
  } catch (e) {
    error.value = e.message || '会话删除失败，请稍后再试。'
  } finally {
    historyLoading.value = false
  }
}

async function refreshConversations() {
  try {
    const data = await fetchChatConversations(20)
    conversations.value = data.map(toConversationSummary)
  } catch {
    // Sending already succeeded; keep the current chat visible if list refresh fails.
  }
}

function scrollToBottom() {
  const el = document.querySelector('.chat-messages')
  if (el) el.scrollTop = el.scrollHeight
}

function resizeComposer() {
  const el = composerRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 140)}px`
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function createConversationId() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID()
  }
  return `conv-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function toViewMessage(message) {
  const normalized = normalizeResult(message)
  return {
    id: normalized.id || `${normalized.role}-${normalized.createdAt || Date.now()}`,
    role: normalized.role,
    content: normalized.content || '',
    suggestedActions: parseSuggestedActions(normalized.suggestedActions),
    conversationId: normalized.conversationId,
    createdAt: normalized.createdAt,
  }
}

function toConversationSummary(conversation) {
  const normalized = normalizeResult(conversation)
  return {
    conversationId: normalized.conversationId,
    title: truncateText(normalized.title || normalized.lastMessage || '未命名会话', 28),
    lastMessage: truncateText(normalized.lastMessage || '', 42),
    updatedAt: normalized.updatedAt,
    messageCount: normalized.messageCount || 0,
  }
}

function truncateText(value, maxLength) {
  if (!value || value.length <= maxLength) return value || ''
  return `${value.slice(0, maxLength)}...`
}

function parseSuggestedActions(value) {
  if (!value) return []
  if (Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}
</script>

<template>
  <div class="chat-view">
    <header class="view-header">
      <div class="conversation-heading">
        <div class="status-pill">
          <span></span>
          Belong is ready
        </div>
        <h1 class="view-title">Talk through the problem.</h1>
      </div>
      <div class="today-card">
        <span>Today</span>
        <strong>Low pressure</strong>
        <button type="button" class="new-chat-button" @click="startNewChat">
          <PlusCircle :size="16" :stroke-width="2" />
          新对话
        </button>
        <div v-if="conversations.length" class="conversation-list" aria-label="历史会话">
          <div
            v-for="conversation in conversations"
            :key="conversation.conversationId"
            class="conversation-row"
            :class="{ active: conversation.conversationId === conversationId }"
          >
            <button
              type="button"
              class="delete-conversation-button"
              aria-label="删除会话"
              title="删除会话"
              @click="deleteConversation(conversation, $event)"
            >
              ❌
            </button>
            <button
              type="button"
              class="conversation-item"
              @click="openConversation(conversation.conversationId)"
            >
              <span>{{ conversation.title }}</span>
              <small>{{ conversation.lastMessage }}</small>
            </button>
          </div>
        </div>
      </div>
    </header>

    <section v-if="historyLoading" class="empty-board loading-board">
      <div class="hero-panel loading-panel">
        <div class="typing-indicator">
          <span></span><span></span><span></span>
        </div>
      </div>
    </section>

    <section v-else-if="!messages.length" class="empty-board">
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
        <div class="message-body">
          <div class="msg-content">{{ msg.content }}</div>
          <div v-if="msg.suggestedActions && msg.suggestedActions.length" class="suggested-actions">
            <span
              v-for="(action, i) in msg.suggestedActions"
              :key="i"
              class="action-chip"
            >{{ action }}</span>
          </div>
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
        ref="composerRef"
        v-model="messageInput"
        class="composer-input"
        placeholder="Say something..."
        rows="1"
        :disabled="loading"
        @input="resizeComposer"
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
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  grid-template-rows: auto minmax(0, 1fr) auto auto;
  column-gap: 36px;
  height: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 28px 36px;
}

.view-header {
  display: contents;
}

.conversation-heading {
  grid-column: 1;
  grid-row: 1;
  padding-bottom: 18px;
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

.today-card {
  grid-column: 2;
  grid-row: 1 / 5;
  align-self: stretch;
  display: flex;
  flex-direction: column;
  min-height: 0;
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

.new-chat-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 100%;
  min-height: 36px;
  margin-top: 14px;
  border: 1px solid #dce5f1;
  border-radius: 12px;
  background: #ffffff;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}

.new-chat-button:hover {
  border-color: #bfdbfe;
  background: #eef4ff;
  transform: translateY(-1px);
}

.conversation-list {
  display: grid;
  align-content: start;
  gap: 8px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #e7edf5;
  min-height: 0;
  max-height: none;
  overflow-y: auto;
}

.conversation-row {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid transparent;
  border-radius: 10px;
  padding: 4px 8px 4px 4px;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.conversation-row:hover,
.conversation-row.active {
  border-color: #dbe6f5;
  background: #ffffff;
}

.conversation-item {
  display: grid;
  gap: 3px;
  min-width: 0;
  flex: 1;
  width: 100%;
  padding: 7px 3px;
  border: 0;
  background: transparent;
  color: #111827;
  text-align: left;
}

.delete-conversation-button {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  border-radius: 999px;
  background: #fff1f2;
  font-size: 12px;
  line-height: 1;
  opacity: 1;
  box-shadow: 0 0 0 1px #fecdd3;
  transition: background 0.15s ease, transform 0.15s ease;
}

.delete-conversation-button:hover {
  background: #ffe4e6;
  transform: scale(1.06);
}

.conversation-item span,
.conversation-item small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-item span {
  font-size: 12px;
  font-weight: 800;
}

.conversation-item small {
  color: #718096;
  font-size: 11px;
  line-height: 1.35;
}

.empty-board {
  grid-column: 1;
  grid-row: 2;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(280px, 0.9fr);
  gap: 18px;
  align-content: center;
  padding: 24px 0;
}

.loading-board {
  grid-template-columns: 1fr;
  align-content: start;
}

.loading-panel {
  min-height: 180px;
  display: grid;
  place-items: center;
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
  grid-column: 1;
  grid-row: 2;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  width: 100%;
  min-height: 0;
  overflow-y: auto;
  padding: 24px 0 22px;
  margin: 0;
}

.message {
  display: flex;
  width: 100%;
  margin-bottom: 14px;
}

.message.user {
  justify-content: flex-end;
}

.message.assistant {
  justify-content: flex-start;
}

.message-body {
  display: grid;
  justify-items: start;
  max-width: min(76%, 720px);
}

.message.user .message-body {
  justify-items: end;
  max-width: min(70%, 640px);
}

.message.user .msg-content {
  width: fit-content;
  max-width: 100%;
  background: #2563eb;
  color: #fff;
  border-radius: 14px 14px 4px 14px;
  padding: 10px 15px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.message.assistant .msg-content {
  width: fit-content;
  max-width: 100%;
  background: #f7f9fc;
  color: #374151;
  border: 1px solid #eef2f7;
  border-radius: 14px 14px 14px 4px;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
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
  grid-column: 1;
  grid-row: 3;
  margin: 0 0 12px;
}

.chat-composer {
  grid-column: 1;
  grid-row: 4;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 58px;
  align-items: flex-end;
  gap: 12px;
  width: 100%;
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
  resize: none;
  overflow-y: auto;
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
    grid-template-columns: 1fr;
    grid-template-rows: auto auto minmax(0, 1fr) auto auto;
    padding: 24px;
  }

  .conversation-heading {
    grid-column: 1;
    grid-row: 1;
  }

  .today-card {
    grid-column: 1;
    grid-row: 2;
    align-self: auto;
    margin-bottom: 18px;
  }

  .empty-board,
  .chat-messages {
    grid-column: 1;
    grid-row: 3;
  }

  .chat-error {
    grid-column: 1;
    grid-row: 4;
  }

  .chat-composer {
    grid-column: 1;
    grid-row: 5;
  }

  .chat-messages {
    padding-top: 12px;
  }

  .empty-board {
    grid-template-columns: 1fr;
  }

  .message-body,
  .message.user .message-body {
    max-width: 86%;
  }
}
</style>
