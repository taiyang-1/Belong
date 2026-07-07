# Belong Backend MVP Development Plan

## 1. Project Goal

Belong is a personal AI product built around a shared memory layer.

The backend MVP must connect three user-facing AI capabilities and one background memory extraction capability:

- Chat: user talks with Belong.
- Organize: user asks Belong to process text, documents, or OCR output.
- Plan: user asks Belong to create, adjust, or review plans.
- Memory Extract: background Dify workflow that turns interaction events into persistent memories.

The MVP backend should prove this loop:

```text
User request
  -> backend reads memories
  -> backend calls the correct Dify flow
  -> Dify returns user-facing result + memory_event
  -> backend calls Memory Extract workflow
  -> backend stores extracted memories in MySQL
  -> future requests use those memories
```

V1 does not need login. Use a fixed demo user:

```text
demo-user
```

## 2. Technology Stack

Use the existing Spring Boot project.

Recommended stack:

- Java 21
- Spring Boot 3.5.x
- Maven
- Spring Web
- MyBatis
- MySQL
- Lombok

Backend port:

```text
8090
```

Reason: local `8080` is already used by Nacos.

## 3. Dify Configuration

Dify base URL:

```text
https://api.dify.ai/v1
```

Do not hard-code real Dify API keys in source code.

Use environment variables:

```text
BELONG_DIFY_CHAT_API_KEY
BELONG_DIFY_ORGANIZE_API_KEY
BELONG_DIFY_PLAN_API_KEY
BELONG_DIFY_MEMORY_API_KEY
```

Application config should look like:

```yaml
server:
  port: 8090

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/belong?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${BELONG_DB_USERNAME:root}
    password: ${BELONG_DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  configuration:
    map-underscore-to-camel-case: true

belong:
  demo-user-id: demo-user
  dify:
    base-url: https://api.dify.ai/v1
    chat-api-key: ${BELONG_DIFY_CHAT_API_KEY}
    organize-api-key: ${BELONG_DIFY_ORGANIZE_API_KEY}
    plan-api-key: ${BELONG_DIFY_PLAN_API_KEY}
    memory-api-key: ${BELONG_DIFY_MEMORY_API_KEY}
```

## 4. Dify Flow Types

The backend must treat Dify flows differently by type.

| Capability | Dify Type | Endpoint |
|---|---|---|
| Chat | Chatflow | `POST /chat-messages` |
| Organize | Workflow | `POST /workflows/run` |
| Plan | Workflow | `POST /workflows/run` |
| Memory Extract | Workflow | `POST /workflows/run` |

## 5. Dify Request Shapes

### 5.1 Workflow Request

Used by Organize, Plan, Memory Extract.

```http
POST https://api.dify.ai/v1/workflows/run
Authorization: Bearer ${FLOW_API_KEY}
Content-Type: application/json
```

Body:

```json
{
  "inputs": {},
  "response_mode": "blocking",
  "user": "demo-user"
}
```

### 5.2 Chatflow Request

Used by Chat.

```http
POST https://api.dify.ai/v1/chat-messages
Authorization: Bearer ${BELONG_DIFY_CHAT_API_KEY}
Content-Type: application/json
```

Body:

```json
{
  "inputs": {
    "memory_context": "...",
    "profile_context": "...",
    "recent_context": "..."
  },
  "query": "用户当前消息",
  "response_mode": "blocking",
  "user": "demo-user"
}
```

## 6. Dify Flow Input Contracts

### 6.1 Chat Flow

Inputs:

```json
{
  "query": "用户当前消息，由 Dify Chatflow 默认 userinput.query 接收",
  "memory_context": "后端拼接出的相关记忆",
  "profile_context": "用户偏好、Belong 语气设置",
  "recent_context": "最近几轮会话摘要"
}
```

Expected output JSON:

```json
{
  "reply": "给用户看的回复",
  "suggested_actions": ["可选下一步操作1", "可选下一步操作2"],
  "memory_event": {
    "source": "chat",
    "action": "user_chat",
    "summary": "这次对话的后台摘要",
    "important_points": ["可能值得记住的信息1"]
  }
}
```

### 6.2 Organize Flow

Inputs:

```json
{
  "content": "用户提交的文本内容",
  "content_type": "text | image_ocr | document",
  "user_request": "用户想让 Belong 怎么处理",
  "memory_context": "后端拼接出的相关记忆",
  "profile_context": "用户偏好、Belong 语气设置"
}
```

Expected output JSON:

```json
{
  "title": "本次整理标题",
  "answer": "给用户看的主要结果",
  "summary": "内容摘要",
  "key_points": ["重点1", "重点2"],
  "todos": ["待办1", "待办2"],
  "memory_event": {
    "source": "organize",
    "action": "user_processed_content",
    "summary": "这次资料处理的后台摘要",
    "important_points": ["可能值得记住的信息1"]
  }
}
```

### 6.3 Plan Flow

Inputs:

```json
{
  "plan_action": "create | adjust | review",
  "user_goal": "用户目标、调整请求或复盘内容",
  "current_plan": "当前已有计划，可为空",
  "progress_context": "当前进度，可为空",
  "memory_context": "后端拼接出的相关记忆",
  "profile_context": "用户偏好、Belong 语气设置"
}
```

Expected output JSON:

```json
{
  "plan_title": "计划标题",
  "plan_summary": "计划摘要",
  "phases": [
    {
      "name": "阶段名称",
      "focus": "阶段重点",
      "duration": "阶段周期"
    }
  ],
  "today_tasks": [
    {
      "title": "任务标题",
      "estimated_minutes": 30,
      "difficulty": "easy"
    }
  ],
  "review_questions": ["复盘问题1", "复盘问题2"],
  "memory_event": {
    "source": "plan",
    "action": "user_created_or_updated_plan",
    "summary": "这次计划行为的后台摘要",
    "important_points": ["可能值得记住的信息1"]
  }
}
```

### 6.4 Memory Extract Flow

Inputs:

```json
{
  "memory_event": "Chat / Organize / Plan 输出的 memory_event JSON 字符串",
  "memory_snapshot": "当前用户已有记忆摘要，用于去重"
}
```

Expected output JSON:

```json
{
  "should_remember": true,
  "memories": [
    {
      "type": "goal",
      "content": "用户正在准备2027年计算机技术方向研究生考试",
      "confidence": 0.95,
      "sensitivity": "medium",
      "expires_in_days": null,
      "reason": "这是用户的长期学习目标，会影响后续计划和对话"
    }
  ]
}
```

## 7. Database Design

Create database:

```sql
CREATE DATABASE IF NOT EXISTS belong DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

MVP memory table:

```sql
CREATE TABLE memories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(64) NOT NULL,
  type VARCHAR(32) NOT NULL,
  content TEXT NOT NULL,
  confidence DECIMAL(3,2),
  sensitivity VARCHAR(16),
  expires_at DATETIME NULL,
  source VARCHAR(32),
  reason TEXT,
  is_archived BOOLEAN DEFAULT FALSE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

Field notes:

- `user_id`: use `demo-user` in V1.
- `type`: one of `fact`, `event`, `preference`, `goal`, `document_insight`.
- `content`: memory text, should be user-centered.
- `confidence`: Dify extraction confidence.
- `sensitivity`: `low`, `medium`, or `high`.
- `expires_at`: calculated from `expires_in_days`; null means no expiry.
- `source`: `chat`, `organize`, or `plan`.
- `is_archived`: soft delete flag.

## 8. Backend Package Structure

Use existing root package, likely:

```text
com.example.belong
```

Recommended structure:

```text
com.example.belong
├─ config
│  └─ BelongProperties
├─ controller
│  ├─ HealthController
│  ├─ MemoryController
│  ├─ ChatController
│  ├─ OrganizeController
│  └─ PlanController
├─ dto
│  ├─ ChatRequest
│  ├─ OrganizeRequest
│  ├─ PlanRequest
│  ├─ BelongResponse
│  └─ DifyResponse
├─ entity
│  └─ Memory
├─ mapper
│  └─ MemoryMapper
├─ service
│  ├─ DifyService
│  ├─ MemoryService
│  ├─ BelongAiService
│  ├─ ChatService
│  ├─ OrganizeService
│  └─ PlanService
└─ exception
   └─ GlobalExceptionHandler
```

For V1, `ChatService`, `OrganizeService`, and `PlanService` can be collapsed into `BelongAiService` if simpler.

## 9. API Design

### 9.1 Health Check

```http
GET /api/health
```

Response:

```json
{
  "status": "ok"
}
```

### 9.2 List Memories

```http
GET /api/memories
```

Response:

```json
{
  "memories": [
    {
      "id": 1,
      "type": "goal",
      "content": "用户正在准备2027年计算机技术方向研究生考试",
      "confidence": 0.95,
      "sensitivity": "medium",
      "source": "organize",
      "createdAt": "2026-07-06T17:00:00"
    }
  ]
}
```

### 9.3 Archive Memory

```http
DELETE /api/memories/{id}
```

Behavior: soft delete by setting `is_archived = true`.

Response:

```json
{
  "success": true
}
```

### 9.4 Organize

```http
POST /api/organize
Content-Type: application/json
```

Request:

```json
{
  "content": "学习规划文本...",
  "contentType": "text",
  "userRequest": "帮我整理重点和待办",
  "profileContext": "用户喜欢具体、低压力、不要太鸡血的建议。"
}
```

Backend behavior:

```text
1. Resolve userId = demo-user.
2. Read active memories.
3. Build memory_context.
4. Call Organize Workflow.
5. Parse organize result.
6. Extract memory_event from result.
7. Call Memory Extract Workflow.
8. Save extracted memories.
9. Return organize result and saved memories.
```

Response:

```json
{
  "result": {
    "title": "...",
    "answer": "...",
    "summary": "...",
    "key_points": [],
    "todos": [],
    "memory_event": {}
  },
  "savedMemories": []
}
```

### 9.5 Chat

```http
POST /api/chat
Content-Type: application/json
```

Request:

```json
{
  "message": "我今天不想学了，有点烦。",
  "profileContext": "用户喜欢温和、具体、低压力的建议。",
  "recentContext": ""
}
```

Backend behavior:

```text
1. Resolve userId = demo-user.
2. Read active memories.
3. Build memory_context.
4. Call Chatflow /chat-messages.
5. Parse chat result.
6. Extract memory_event.
7. Call Memory Extract Workflow.
8. Save extracted memories.
9. Return chat result and saved memories.
```

Response:

```json
{
  "result": {
    "reply": "...",
    "suggested_actions": [],
    "memory_event": {}
  },
  "savedMemories": []
}
```

### 9.6 Plan

```http
POST /api/plan
Content-Type: application/json
```

Request:

```json
{
  "planAction": "create",
  "userGoal": "帮我制定一个接下来7天的考研恢复学习计划。",
  "currentPlan": "",
  "progressContext": "",
  "profileContext": "用户喜欢具体、低压力、不要太鸡血的建议。"
}
```

Backend behavior:

```text
1. Resolve userId = demo-user.
2. Read active memories.
3. Build memory_context.
4. Call Plan Workflow.
5. Parse plan result.
6. Extract memory_event.
7. Call Memory Extract Workflow.
8. Save extracted memories.
9. Return plan result and saved memories.
```

Response:

```json
{
  "result": {
    "plan_title": "...",
    "plan_summary": "...",
    "phases": [],
    "today_tasks": [],
    "review_questions": [],
    "memory_event": {}
  },
  "savedMemories": []
}
```

## 10. Memory Context Strategy

V1 does not need vector search.

Implement simple memory context:

```text
Fetch latest 20 active memories for the user.
Sort by created_at DESC.
Concatenate as plain text.
```

Example:

```text
用户记忆：用户计划2027年考研计算机技术，优先上海或杭州高校。
用户记忆：用户工作日晚上约有2小时学习时间。
用户记忆：用户偏好具体小任务、低压力引导。
```

Future versions can add:

- keyword matching
- embedding search
- memory type filtering
- sensitivity filtering
- expiry cleanup

## 11. Memory Save Strategy

When Memory Extract returns memories:

```json
{
  "should_remember": true,
  "memories": []
}
```

Backend should:

1. Ignore if `should_remember = false`.
2. Ignore item if `content` is empty.
3. Ignore exact duplicate content for same user.
4. Convert `expires_in_days` into `expires_at`.
5. Save each memory.

V1 duplicate check can be exact string matching.

Future versions can add fuzzy duplicate checks.

## 12. Dify Response Parsing

Dify may return JSON as a string inside an output field.

Backend should support both:

```json
{
  "data": {
    "outputs": {
      "organize_result": "{...}"
    }
  }
}
```

and:

```json
{
  "answer": "{...}"
}
```

Recommended parsing approach:

```text
1. Read workflow output field.
2. If value is JSON object, use it directly.
3. If value is text, trim it.
4. Extract substring from first "{" to last "}".
5. Parse as JSON.
6. If parsing fails, return raw text with an error marker.
```

## 13. Error Handling

Implement a global exception handler returning:

```json
{
  "error": "short_error_code",
  "message": "human readable message"
}
```

Important cases:

- Missing Dify API key.
- Dify API timeout.
- Dify API non-2xx response.
- Dify output is not valid JSON.
- MySQL connection failure.
- Required request field is empty.

V1 should not crash on memory extraction failure.

If user-facing flow succeeds but Memory Extract fails:

```text
Return user-facing result anyway.
Log memory extraction failure.
savedMemories = []
```

## 14. Development Order

Build in this order:

1. Configure app port `8090`.
2. Configure MySQL connection.
3. Create `memories` table.
4. Add `GET /api/health`.
5. Add `Memory` entity and `MemoryMapper`.
6. Add `GET /api/memories`.
7. Add `DifyService` for workflow calls.
8. Implement `/api/organize` without saving memories.
9. Implement Memory Extract call.
10. Save extracted memories.
11. Test `/api/organize -> /api/memories`.
12. Add `DifyService` chat call.
13. Implement `/api/chat`.
14. Test chat reads previously saved memories.
15. Implement `/api/plan`.
16. Test plan reads memories and saves new memory events.

## 15. MVP Acceptance Tests

### Test 1: Health

```http
GET http://localhost:8090/api/health
```

Expected:

```json
{
  "status": "ok"
}
```

### Test 2: Organize creates memories

Call:

```http
POST http://localhost:8090/api/organize
```

Body:

```json
{
  "content": "我最近决定开始准备2027年研究生考试，目标专业是计算机技术。白天上班，晚上大概只能稳定学习2小时。我喜欢明确的小任务，不喜欢太激进、太空泛的计划。",
  "contentType": "text",
  "userRequest": "帮我整理这份学习规划，提炼重点和接下来要做的事",
  "profileContext": "用户喜欢温和、具体、低压力、不要太鸡血的建议。"
}
```

Expected:

- Response has organize result.
- Response has saved memories.
- `GET /api/memories` shows考研相关记忆.

### Test 3: Chat uses memories

Call:

```http
POST http://localhost:8090/api/chat
```

Body:

```json
{
  "message": "我今天不太想学了，感觉计划好多，有点烦。",
  "profileContext": "用户喜欢温和、具体、低压力、不要太鸡血的建议。",
  "recentContext": ""
}
```

Expected:

- Response naturally mentions existing考研/时间/低压力 context.
- It does not say "根据记忆库".
- It returns `memory_event`.

### Test 4: Plan uses memories

Call:

```http
POST http://localhost:8090/api/plan
```

Body:

```json
{
  "planAction": "create",
  "userGoal": "帮我制定一个接下来7天的考研恢复学习计划。",
  "currentPlan": "",
  "progressContext": "",
  "profileContext": "用户喜欢温和、具体、低压力、不要太鸡血的建议。"
}
```

Expected:

- Plan uses 2-hour evening constraint.
- Tasks are concrete and no more than 3.
- Response returns `memory_event`.
- New memories may be saved.

## 16. Not Included in V1

Do not implement these in MVP unless explicitly requested:

- real user registration/login
- JWT
- payment
- file upload
- OCR
- PDF parsing
- vector database
- memory embedding search
- WebSocket streaming
- admin dashboard
- mobile app

## 17. V2 Roadmap

After MVP works:

1. Add real users and authentication.
2. Add frontend.
3. Add memory management page.
4. Add file upload and OCR.
5. Add PDF/document parsing.
6. Add memory relevance search.
7. Add memory sensitivity controls.
8. Add async memory extraction queue.
9. Add conversation history.
10. Add deployment configuration.
