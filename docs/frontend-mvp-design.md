# Belong Frontend MVP Design

## 1. Goal

Belong Frontend MVP is a Vue 3 + Vite web app for the first usable version of Belong.

Belong has three visible user entrances:

- Chat: talk with Belong as a personal AI companion.
- Organize: paste text and ask Belong to organize it.
- Plan: ask Belong to create, adjust, or review plans.

Memory is not a visible product module in V1. It is a backend capability.

The MVP should prove this loop:

```text
User uses Chat / Organize / Plan
  -> backend reads stored memories
  -> backend calls the relevant Dify flow with memory context
  -> Dify returns a personalized answer
  -> backend extracts and saves useful memory in MySQL
```

The user should feel that Belong understands them, but the UI should not expose the memory database directly.

## 2. Product Positioning

Belong should feel like:

```text
ChatGPT + Notion + a quiet personal workspace
```

It should not feel like:

```text
a company admin system
a database viewer
a marketing website
a complex productivity platform
```

The memory layer should be invisible but effective. The best evidence of memory is not a memory list; it is better answers in Chat, Organize, and Plan.

## 3. Scope

### In Scope

- Vue 3 + Vite frontend project under `frontend/`.
- Three main views: Chat, Organize, Plan.
- Left sidebar navigation.
- Main workspace area.
- API integration with the existing Spring Boot backend.
- Loading, error, and empty states.
- Basic responsive behavior for laptop and desktop screens.

### Out of Scope

- Visible Memory page.
- Right-side Memory panel.
- Memory cards or memory management UI.
- Login and registration.
- Multi-user switching.
- File upload.
- Mobile app packaging.
- Payment.
- Complex animation.
- Direct Dify API calls from the frontend.
- Full markdown editor.

## 4. Technology Stack

Use:

- Vue 3
- Vite
- JavaScript
- Axios
- Scoped CSS
- `lucide-vue-next` for icons

Do not add a heavy UI framework in V1. The interface is small enough to build with custom components.

## 5. Project Location

Frontend lives inside the existing Belong repository:

```text
/Users/yufeng/code/Belong/frontend
```

The backend remains the Spring Boot project at repository root.

## 6. Frontend Project Structure

```text
frontend/
├── index.html
├── package.json
├── vite.config.js
├── .env.development
└── src/
    ├── main.js
    ├── App.vue
    ├── api/
    │   ├── http.js
    │   ├── chatApi.js
    │   ├── organizeApi.js
    │   └── planApi.js
    ├── components/
    │   ├── layout/
    │   │   ├── AppShell.vue
    │   │   └── Sidebar.vue
    │   └── common/
    │       ├── EmptyState.vue
    │       ├── LoadingButton.vue
    │       ├── ResultSection.vue
    │       └── StatusMessage.vue
    ├── views/
    │   ├── ChatView.vue
    │   ├── OrganizeView.vue
    │   └── PlanView.vue
    ├── styles/
    │   └── base.css
    └── utils/
        └── normalizeResult.js
```

## 7. Layout Design

Use a two-column app shell:

```text
┌──────────────────────────────────────────────┐
│ Sidebar       │ Main Workspace               │
│               │                              │
│ Belong        │ Current feature view          │
│ Chat          │ Input area                    │
│ Organize      │ AI result area                │
│ Plan          │                              │
└──────────────────────────────────────────────┘
```

### Sidebar

Purpose:

- Show product identity.
- Switch between the three visible modes.

Items:

- Chat
- Organize
- Plan

Visual direction:

- Fixed width around `220px`.
- White or near-white background.
- Simple icon + text navigation.
- Active item should have a soft blue background.

### Main Workspace

Purpose:

- Host the current view.
- Keep user input and AI output easy to scan.

Visual direction:

- Light gray page background.
- Content max width around `860px`.
- Use sections, not many nested cards.
- Keep headings compact.

## 8. Visual Style

Belong should look calm, personal, and reliable.

Recommended tokens:

```text
background: #F7F8FA
surface: #FFFFFF
surface-muted: #F1F5F9
border: #E5E7EB
text-main: #111827
text-muted: #6B7280
primary: #2563EB
primary-soft: #DBEAFE
accent: #14B8A6
danger: #DC2626
radius: 8px
```

Avoid:

- visible memory database panels
- large marketing hero areas
- purple-blue gradient backgrounds
- decorative blobs
- excessive shadows
- card inside card layouts
- oversized headings in tool panels

## 9. API Base URL

In local development, use Vite proxy and relative API paths:

```env
VITE_API_BASE_URL=/
```

Vite proxy forwards `/api/*` to:

```text
http://localhost:8090
```

The frontend calls only the Spring Boot backend. It must not call Dify directly.

## 10. Backend API Contracts

### 10.1 Chat

Endpoint:

```http
POST /api/chat
```

Request:

```json
{
  "message": "我今天不太想学了，感觉计划好多，有点烦。",
  "profileContext": "用户喜欢温和、具体、低压力、不要太鸡血的建议。",
  "recentContext": ""
}
```

Response:

```json
{
  "result": {
    "reply": "...",
    "suggested_actions": ["..."],
    "memory_event": {}
  },
  "savedMemories": []
}
```

Frontend display:

- Show user message.
- Show `result.reply`.
- Show `result.suggested_actions` as small action chips or a simple list.
- Do not display `memory_event`.
- Do not display `savedMemories`.

### 10.2 Organize

Endpoint:

```http
POST /api/organize
```

Request:

```json
{
  "content": "text to organize",
  "contentType": "text",
  "userRequest": "帮我整理重点和待办",
  "profileContext": "用户喜欢温和、具体、低压力、不要太鸡血的建议。"
}
```

Response:

```json
{
  "result": {
    "title": "...",
    "answer": "...",
    "summary": "...",
    "key_points": ["..."],
    "todos": ["..."],
    "memory_event": {}
  },
  "savedMemories": []
}
```

Frontend display:

- Show `title`.
- Show `answer`.
- Show `summary`.
- Show `key_points`.
- Show `todos`.
- Do not display `memory_event`.
- Do not display `savedMemories`.

### 10.3 Plan

Endpoint:

```http
POST /api/plan
```

Request:

```json
{
  "planAction": "create",
  "userGoal": "帮我制定一个接下来7天的考研恢复学习计划。",
  "currentPlan": "",
  "progressContext": "",
  "profileContext": "用户喜欢温和、具体、低压力、不要太鸡血的建议。"
}
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

Frontend display:

- Show `plan_title`.
- Show `plan_summary`.
- Show `phases`.
- Show `today_tasks`.
- Show `review_questions`.
- Do not display `memory_event`.
- Do not display `savedMemories`.

## 11. View Details

### 11.1 ChatView

Purpose:

Let the user talk with Belong naturally.

UI:

- Header: `Chat`
- Short subtitle: `Talk with Belong. It responds with your situation in mind.`
- Message list.
- Fixed input composer at bottom of main workspace.
- Submit button with loading state.

Local state:

- `messages`
- `messageInput`
- `loading`
- `error`

Behavior:

- On submit, append user message.
- Call `chatApi.sendMessage`.
- Append assistant reply.
- If request fails, show a compact error message and keep user input available.

### 11.2 OrganizeView

Purpose:

Let the user paste text and get structured output.

UI:

- Header: `Organize`
- Textarea for `content`.
- Small input for `userRequest`.
- Optional collapsed advanced field for `profileContext`.
- Submit button.
- Result area.

Default values:

```text
contentType: text
userRequest: 帮我整理重点和待办
profileContext: 用户喜欢温和、具体、低压力、不要太鸡血的建议。
```

Behavior:

- Call `organizeApi.organize`.
- Render structured result.
- The backend handles memory extraction after the response.

### 11.3 PlanView

Purpose:

Let the user create or adjust a plan.

UI:

- Header: `Plan`
- Segmented control for `planAction`.
- Textarea for `userGoal`.
- Optional fields for `currentPlan` and `progressContext`.
- Submit button.
- Result area with tasks and review questions.

Default values:

```text
planAction: create
profileContext: 用户喜欢温和、具体、低压力、不要太鸡血的建议。
```

Behavior:

- Call `planApi.createOrUpdatePlan`.
- Render structured result.
- The backend handles memory extraction after the response.

## 12. Memory Behavior

Memory is backend-only in V1.

Frontend rules:

- Do not show a Memory menu item.
- Do not show a Memory page.
- Do not show a right-side Memory panel.
- Do not show memory cards.
- Do not show `memory_event`.
- Do not show `savedMemories`.

Backend rules:

- Before calling Chat, Organize, or Plan Dify flow, the backend should read memory from MySQL and inject it into Dify inputs.
- After receiving `memory_event`, the backend should call Memory Extract and save useful memories.
- Future requests should benefit from saved memories automatically.

Product rule:

```text
The user should experience memory through better answers, not through a visible database.
```

## 13. Error Handling

Each view should handle:

- backend not running
- Dify timeout
- malformed response
- empty input

User-facing error text should be short:

```text
Belong 暂时没有回应，请稍后再试。
请先输入内容。
后端服务可能没有启动。
```

Do not show raw stack traces in the UI.

## 14. Loading States

Use one loading state per action:

- Chat send button: `Thinking...`
- Organize button: `Organizing...`
- Plan button: `Planning...`

Avoid full-page loading overlays.

## 15. MVP Acceptance Criteria

The frontend MVP is complete when:

- User can open the app locally.
- User can switch between Chat, Organize, and Plan.
- Chat calls `POST /api/chat` and displays `reply`.
- Organize calls `POST /api/organize` and displays title, answer, summary, key points, and todos.
- Plan calls `POST /api/plan` and displays title, summary, phases, tasks, and review questions.
- No visible Memory menu exists.
- No visible Memory panel exists.
- No raw `memory_event` is shown.
- No `savedMemories` data is shown.
- No raw JSON blob is shown unless used in a developer/debug area.
- The UI remains usable on common laptop widths.

## 16. Recommended Implementation Order

1. Create Vue 3 + Vite project in `frontend/`.
2. Add base styles and theme tokens.
3. Build `AppShell`, `Sidebar`, and route-less view switching.
4. Add API client files.
5. Build `ChatView`.
6. Build `OrganizeView`.
7. Build `PlanView`.
8. Run against local backend on `http://localhost:8090`.

## 17. Local Run Commands

From repository root:

```bash
cd frontend
npm install
npm run dev
```

Expected frontend URL:

```text
http://localhost:5173
```

Backend must be running on:

```text
http://localhost:8090
```
