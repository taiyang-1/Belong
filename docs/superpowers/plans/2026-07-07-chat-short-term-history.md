# Chat Short-Term History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Chat short-term history so Belong can show the latest 20 chat messages and start a fresh visible conversation without deleting history.

**Architecture:** Persist chat turns in MySQL with a `conversation_id`. The backend owns recent chat context and injects it into Dify; the frontend only displays the latest records and sends the active conversation id.

**Tech Stack:** Spring Boot, MyBatis annotations, MySQL, Vue 3, Vite, Axios.

---

### Task 1: Backend Chat Message Storage

**Files:**
- Create: `/Users/yufeng/code/Belong/src/main/java/com/example/belong/entity/ChatMessage.java`
- Create: `/Users/yufeng/code/Belong/src/main/java/com/example/belong/mapper/ChatMessageMapper.java`
- Create: `/Users/yufeng/code/Belong/src/main/java/com/example/belong/service/ChatMessageService.java`
- Create: `/Users/yufeng/code/Belong/src/test/java/com/example/belong/service/ChatMessageServiceTest.java`
- Modify: `/Users/yufeng/code/Belong/docs/sql/init.sql`

- [ ] Add service tests for formatting context and limiting messages.
- [ ] Implement entity, mapper, and service.
- [ ] Add `chat_messages` table SQL.

### Task 2: Chat API Integration

**Files:**
- Modify: `/Users/yufeng/code/Belong/src/main/java/com/example/belong/dto/ChatRequest.java`
- Modify: `/Users/yufeng/code/Belong/src/main/java/com/example/belong/controller/ChatController.java`
- Modify: `/Users/yufeng/code/Belong/src/main/java/com/example/belong/service/BelongAiService.java`

- [ ] Accept `conversationId` in chat requests.
- [ ] Add `GET /api/chat/messages?limit=20`.
- [ ] Save user and assistant messages around the Dify call.
- [ ] Build `recent_context` from stored history when the caller does not provide one.

### Task 3: Frontend Chat UI

**Files:**
- Modify: `/Users/yufeng/code/Belong/frontend/src/api/chatApi.js`
- Modify: `/Users/yufeng/code/Belong/frontend/src/views/ChatView.vue`

- [ ] Load the latest 20 chat messages on mount.
- [ ] Generate and send a `conversationId`.
- [ ] Add a New Chat button that clears the visible thread and starts a new conversation id.
- [ ] Keep the chat panel height content-aware, only scrolling after a max height.

### Task 4: Verification

- [ ] Run `./mvnw test` from `/Users/yufeng/code/Belong`.
- [ ] Run `npm run build` from `/Users/yufeng/code/Belong/frontend`.
- [ ] If MySQL is running, apply `docs/sql/init.sql` or the new `chat_messages` table statement before manual API testing.
