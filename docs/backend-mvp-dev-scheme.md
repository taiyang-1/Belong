# Belong Backend MVP 开发方案

> 基于 `docs/backend-mvp-development-plan.md`，本文档将开发计划拆解为可执行的开发任务，每个任务包含具体的文件清单、代码骨架和验证标准。

---

## 目录

1. [项目现状](#1-项目现状)
2. [开发阶段总览](#2-开发阶段总览)
3. [Phase 1：基础设施搭建](#phase-1基础设施搭建)
4. [Phase 2：Memory 模块](#phase-2memory-模块)
5. [Phase 3：Dify 服务层](#phase-3dify-服务层)
6. [Phase 4：Organize 接口](#phase-4organize-接口)
7. [Phase 5：Chat 接口](#phase-5chat-接口)
8. [Phase 6：Plan 接口](#phase-6plan-接口)
9. [Phase 7：异常处理与边界情况](#phase-7异常处理与边界情况)
10. [Phase 8：联调验证](#phase-8联调验证)
11. [附录：文件清单汇总](#附录文件清单汇总)

---

## 1. 项目现状

| 项目 | 状态 |
|------|------|
| Spring Boot 3.5.16 | ✅ 已初始化 |
| Java 21 | ✅ 已配置 |
| spring-boot-starter-web | ✅ 已引入 |
| mybatis-spring-boot-starter 3.0.5 | ✅ 已引入 |
| mysql-connector-j | ✅ 已引入 |
| Lombok | ✅ 已引入 |
| application.yaml | ⚠️ 仅含应用名，需补全 |
| 业务代码 | ❌ 尚未开始 |

---

## 2. 开发阶段总览

```
Phase 1: 基础设施搭建        ██░░░░░░░░  配置、Health API
Phase 2: Memory 模块         ███░░░░░░░  实体、Mapper、查询/归档接口
Phase 3: Dify 服务层         ████░░░░░░  Dify API 调用封装
Phase 4: Organize 接口       ██████░░░░  POST /api/organize 全链路
Phase 5: Chat 接口           ████████░░  POST /api/chat 全链路
Phase 6: Plan 接口           █████████░  POST /api/plan 全链路
Phase 7: 异常处理            ██████████  全局异常处理、边界情况
Phase 8: 联调验证            ██████████  端到端测试
```

**开发顺序原则**：严格按 Phase 1 → 8 顺序推进，每个 Phase 完成并验证后再进入下一个。

---

## Phase 1：基础设施搭建

### 目标

补全项目配置，启动后端口为 8090，MySQL 连接正常，`GET /api/health` 返回 `{"status": "ok"}`。

### 1.1 补全 application.yaml

**文件**：`src/main/resources/application.yaml`

```yaml
server:
  port: 8090

spring:
  application:
    name: Belong
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

### 1.2 创建配置类 BelongProperties

**文件**：`src/main/java/com/example/belong/config/BelongProperties.java`

```java
package com.example.belong.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "belong")
public class BelongProperties {
    private String demoUserId;
    private Dify dify = new Dify();

    @Data
    public static class Dify {
        private String baseUrl;
        private String chatApiKey;
        private String organizeApiKey;
        private String planApiKey;
        private String memoryApiKey;
    }
}
```

> 需要在 `BelongApplication` 或任意配置类上加 `@EnableConfigurationProperties(BelongProperties.class)`，或依赖 `@Component` + `@ConfigurationProperties` 的组合（Spring Boot 会自动扫描）。

### 1.3 创建 HealthController

**文件**：`src/main/java/com/example/belong/controller/HealthController.java`

```java
package com.example.belong.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
```

### 1.4 创建数据库和表

在 MySQL 中执行：

```sql
CREATE DATABASE IF NOT EXISTS belong DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE belong;

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

### 验证标准

```bash
# 启动应用
./mvnw spring-boot:run

# 验证 Health
curl http://localhost:8090/api/health
# → {"status":"ok"}
```

---

## Phase 2：Memory 模块

### 目标

完成 Memory 实体的 CRUD 基础能力：列表查询、归档（软删除）。

### 2.1 创建 Memory 实体

**文件**：`src/main/java/com/example/belong/entity/Memory.java`

```java
package com.example.belong.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Memory {
    private Long id;
    private String userId;
    private String type;        // fact, event, preference, goal, document_insight
    private String content;
    private BigDecimal confidence;
    private String sensitivity; // low, medium, high
    private LocalDateTime expiresAt;
    private String source;      // chat, organize, plan
    private String reason;
    private Boolean isArchived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 2.2 创建 MemoryMapper

**文件**：`src/main/java/com/example/belong/mapper/MemoryMapper.java`

```java
package com.example.belong.mapper;

import com.example.belong.entity.Memory;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MemoryMapper {

    @Select("SELECT * FROM memories WHERE user_id = #{userId} AND is_archived = FALSE ORDER BY created_at DESC")
    List<Memory> findActiveByUserId(String userId);

    @Select("SELECT * FROM memories WHERE user_id = #{userId} AND is_archived = FALSE ORDER BY created_at DESC LIMIT #{limit}")
    List<Memory> findLatestActiveByUserId(String userId, int limit);

    @Select("SELECT * FROM memories WHERE id = #{id}")
    Memory findById(Long id);

    @Insert("INSERT INTO memories(user_id, type, content, confidence, sensitivity, expires_at, source, reason, is_archived, created_at, updated_at) " +
            "VALUES(#{userId}, #{type}, #{content}, #{confidence}, #{sensitivity}, #{expiresAt}, #{source}, #{reason}, #{isArchived}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Memory memory);

    @Update("UPDATE memories SET is_archived = TRUE, updated_at = NOW() WHERE id = #{id}")
    int archiveById(Long id);

    @Select("SELECT COUNT(*) FROM memories WHERE user_id = #{userId} AND content = #{content} AND is_archived = FALSE")
    int countByUserIdAndContent(String userId, String content);
}
```

### 2.3 创建 MemoryService

**文件**：`src/main/java/com/example/belong/service/MemoryService.java`

```java
package com.example.belong.service;

import com.example.belong.entity.Memory;
import com.example.belong.mapper.MemoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MemoryMapper memoryMapper;

    public List<Memory> getActiveMemories(String userId) {
        return memoryMapper.findActiveByUserId(userId);
    }

    public List<Memory> getLatestActiveMemories(String userId, int limit) {
        return memoryMapper.findLatestActiveByUserId(userId, limit);
    }

    /**
     * 构建记忆上下文文本，用于注入 Dify 请求。
     * V1 策略：取最近 20 条活跃记忆，按时间倒序拼接。
     */
    public String buildMemoryContext(String userId) {
        List<Memory> memories = getLatestActiveMemories(userId, 20);
        if (memories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Memory m : memories) {
            sb.append("用户记忆：").append(m.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    public void archiveMemory(Long id) {
        memoryMapper.archiveById(id);
    }

    /**
     * 保存记忆列表，带去重检查。
     * V1 去重策略：完全匹配 content + userId。
     */
    public int saveMemories(List<Memory> memories) {
        int saved = 0;
        for (Memory m : memories) {
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            int exists = memoryMapper.countByUserIdAndContent(m.getUserId(), m.getContent());
            if (exists > 0) {
                continue;
            }
            memoryMapper.insert(m);
            saved++;
        }
        return saved;
    }
}
```

### 2.4 创建 MemoryController

**文件**：`src/main/java/com/example/belong/controller/MemoryController.java`

```java
package com.example.belong.controller;

import com.example.belong.config.BelongProperties;
import com.example.belong.entity.Memory;
import com.example.belong.service.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;
    private final BelongProperties belongProperties;

    @GetMapping("/memories")
    public Map<String, Object> listMemories() {
        List<Memory> memories = memoryService.getActiveMemories(belongProperties.getDemoUserId());
        return Map.of("memories", memories);
    }

    @DeleteMapping("/memories/{id}")
    public Map<String, Object> archiveMemory(@PathVariable Long id) {
        memoryService.archiveMemory(id);
        return Map.of("success", true);
    }
}
```

### 验证标准

```bash
# 启动后，列表应为空
curl http://localhost:8090/api/memories
# → {"memories":[]}

# 归档不存在的 ID 也不报错（幂等）
curl -X DELETE http://localhost:8090/api/memories/999
# → {"success":true}
```

---

## Phase 3：Dify 服务层

### 目标

封装 Dify API 的 HTTP 调用，支持 Chatflow 和 Workflow 两种模式，能正确解析 Dify 返回的 JSON。

### 3.1 创建 DTO 类

**文件**：`src/main/java/com/example/belong/dto/ChatRequest.java`

```java
package com.example.belong.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String profileContext;
    private String recentContext;
}
```

**文件**：`src/main/java/com/example/belong/dto/OrganizeRequest.java`

```java
package com.example.belong.dto;

import lombok.Data;

@Data
public class OrganizeRequest {
    private String content;
    private String contentType;   // text, image_ocr, document
    private String userRequest;
    private String profileContext;
}
```

**文件**：`src/main/java/com/example/belong/dto/PlanRequest.java`

```java
package com.example.belong.dto;

import lombok.Data;

@Data
public class PlanRequest {
    private String planAction;    // create, adjust, review
    private String userGoal;
    private String currentPlan;
    private String progressContext;
    private String profileContext;
}
```

**文件**：`src/main/java/com/example/belong/dto/BelongResponse.java`

```java
package com.example.belong.dto;

import lombok.Data;
import java.util.List;

@Data
public class BelongResponse {
    private Object result;
    private List<Object> savedMemories;
}
```

### 3.2 创建 DifyService

**文件**：`src/main/java/com/example/belong/service/DifyService.java`

```java
package com.example.belong.service;

import com.example.belong.config.BelongProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DifyService {

    private final BelongProperties belongProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用 Chatflow（Chat 场景）。
     */
    public JsonNode callChatflow(String apiKey, Map<String, Object> inputs, String query, String user) {
        String url = belongProperties.getDify().getBaseUrl() + "/chat-messages";

        Map<String, Object> body = Map.of(
                "inputs", (Object) inputs,
                "query", query,
                "response_mode", "blocking",
                "user", user
        );

        ResponseEntity<String> response = postJson(url, apiKey, body);
        return parseDifyOutput(response.getBody());
    }

    /**
     * 调用 Workflow（Organize / Plan / Memory Extract 场景）。
     */
    public JsonNode callWorkflow(String apiKey, Map<String, Object> inputs, String user) {
        String url = belongProperties.getDify().getBaseUrl() + "/workflows/run";

        Map<String, Object> body = Map.of(
                "inputs", (Object) inputs,
                "response_mode", "blocking",
                "user", user
        );

        ResponseEntity<String> response = postJson(url, apiKey, body);
        return parseDifyOutput(response.getBody());
    }

    private ResponseEntity<String> postJson(String url, String apiKey, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    /**
     * 解析 Dify 返回的 JSON。
     * 兼容两种格式：
     *   1. {"data": {"outputs": {"xxx": "{...}"}}}  → Workflow
     *   2. {"answer": "{...}"}                       → Chatflow
     *
     * 如果 output 值是 JSON 字符串，则解析为 JsonNode；
     * 如果已经是 JSON 对象，则直接使用。
     */
    public JsonNode parseDifyOutput(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Chatflow 格式：直接从 answer 取
            if (root.has("answer")) {
                return parseJsonValue(root.get("answer"));
            }

            // Workflow 格式：data.outputs 下的第一个值
            if (root.has("data") && root.get("data").has("outputs")) {
                JsonNode outputs = root.get("data").get("outputs");
                // 取 outputs 下第一个字段的值
                var fields = outputs.fields();
                if (fields.hasNext()) {
                    JsonNode value = fields.next().getValue();
                    return parseJsonValue(value);
                }
            }

            // 兜底：直接返回整个响应
            return root;
        } catch (Exception e) {
            log.error("Failed to parse Dify output: {}", responseBody, e);
            return null;
        }
    }

    private JsonNode parseJsonValue(JsonNode node) throws Exception {
        if (node.isObject() || node.isArray()) {
            return node;
        }
        // 文本值：尝试提取 JSON
        String text = node.asText().trim();
        if (text.startsWith("{")) {
            int start = text.indexOf("{");
            int end = text.lastIndexOf("}");
            if (start >= 0 && end > start) {
                return objectMapper.readTree(text.substring(start, end + 1));
            }
        }
        return node;
    }
}
```

### 验证标准

由于依赖外部 Dify API，此阶段验证以单元逻辑为准，确保 HTTP 请求构造正确。真正的端到端验证在 Phase 4-6 中完成。

---

## Phase 4：Organize 接口

### 目标

实现 `POST /api/organize` 的全链路：读取记忆 → 调用 Dify Organize Workflow → 提取 memory_event → 调用 Memory Extract → 保存记忆 → 返回结果。

### 4.1 创建 BelongAiService（核心编排层）

**文件**：`src/main/java/com/example/belong/service/BelongAiService.java`

```java
package com.example.belong.service;

import com.example.belong.config.BelongProperties;
import com.example.belong.dto.BelongResponse;
import com.example.belong.entity.Memory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BelongAiService {

    private final BelongProperties belongProperties;
    private final DifyService difyService;
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 通用编排流程：
     *   1. 读取活跃记忆 → 构建 memory_context
     *   2. 调用对应的 Dify flow
     *   3. 从 Dify 结果中提取 memory_event
     *   4. 调用 Memory Extract workflow
     *   5. 保存提取出的记忆
     *   6. 返回 BelongResponse
     */
    public BelongResponse processOrganize(String content, String contentType,
                                          String userRequest, String profileContext) {
        String userId = belongProperties.getDemoUserId();
        String memoryContext = memoryService.buildMemoryContext(userId);

        // 构造 Dify 输入
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("content", content);
        inputs.put("content_type", contentType);
        inputs.put("user_request", userRequest);
        inputs.put("memory_context", memoryContext);
        inputs.put("profile_context", profileContext);

        // 调用 Organize Workflow
        String apiKey = belongProperties.getDify().getOrganizeApiKey();
        JsonNode organizeResult = difyService.callWorkflow(apiKey, inputs, userId);

        // 提取并处理 memory_event
        List<Memory> savedMemories = extractAndSaveMemories(organizeResult, userId);

        BelongResponse response = new BelongResponse();
        response.setResult(organizeResult);
        response.setSavedMemories(new ArrayList<>(savedMemories));
        return response;
    }

    /**
     * 从 Dify 返回结果中提取 memory_event，调用 Memory Extract，保存记忆。
     * 如果 memory 提取失败，不影响主流程，仅记录日志。
     */
    private List<Memory> extractAndSaveMemories(JsonNode flowResult, String userId) {
        try {
            JsonNode memoryEvent = flowResult.get("memory_event");
            if (memoryEvent == null || memoryEvent.isNull()) {
                return List.of();
            }

            // 构建 memory_snapshot
            String memorySnapshot = memoryService.buildMemoryContext(userId);

            Map<String, Object> extractInputs = new LinkedHashMap<>();
            extractInputs.put("memory_event", memoryEvent.toString());
            extractInputs.put("memory_snapshot", memorySnapshot);

            String memoryApiKey = belongProperties.getDify().getMemoryApiKey();
            JsonNode extractResult = difyService.callWorkflow(memoryApiKey, extractInputs, userId);

            if (extractResult == null) {
                log.warn("Memory Extract returned null");
                return List.of();
            }

            boolean shouldRemember = extractResult.has("should_remember")
                    && extractResult.get("should_remember").asBoolean();
            if (!shouldRemember) {
                return List.of();
            }

            JsonNode memoriesNode = extractResult.get("memories");
            if (memoriesNode == null || !memoriesNode.isArray()) {
                return List.of();
            }

            List<Memory> toSave = new ArrayList<>();
            for (JsonNode node : memoriesNode) {
                Memory m = new Memory();
                m.setUserId(userId);
                m.setType(node.has("type") ? node.get("type").asText() : "fact");
                m.setContent(node.get("content").asText());
                m.setConfidence(node.has("confidence") ? BigDecimal.valueOf(node.get("confidence").asDouble()) : null);
                m.setSensitivity(node.has("sensitivity") ? node.get("sensitivity").asText() : "medium");
                m.setSource(memoryEvent.has("source") ? memoryEvent.get("source").asText() : null);
                m.setReason(node.has("reason") ? node.get("reason").asText() : null);
                m.setIsArchived(false);

                // expires_in_days → expires_at
                if (node.has("expires_in_days") && !node.get("expires_in_days").isNull()) {
                    int days = node.get("expires_in_days").asInt();
                    m.setExpiresAt(LocalDateTime.now().plusDays(days));
                }

                toSave.add(m);
            }

            int saved = memoryService.saveMemories(toSave);
            log.info("Saved {} new memories for user {}", saved, userId);
            return toSave;

        } catch (Exception e) {
            log.error("Memory extraction failed for user {}: {}", userId, e.getMessage(), e);
            return List.of();
        }
    }
}
```

### 4.2 创建 OrganizeController

**文件**：`src/main/java/com/example/belong/controller/OrganizeController.java`

```java
package com.example.belong.controller;

import com.example.belong.dto.BelongResponse;
import com.example.belong.dto.OrganizeRequest;
import com.example.belong.service.BelongAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrganizeController {

    private final BelongAiService belongAiService;

    @PostMapping("/organize")
    public BelongResponse organize(@RequestBody OrganizeRequest request) {
        return belongAiService.processOrganize(
                request.getContent(),
                request.getContentType(),
                request.getUserRequest(),
                request.getProfileContext()
        );
    }
}
```

### 验证标准

```bash
curl -X POST http://localhost:8090/api/organize \
  -H "Content-Type: application/json" \
  -d '{
    "content": "我最近决定开始准备2027年研究生考试，目标专业是计算机技术。白天上班，晚上大概只能稳定学习2小时。",
    "contentType": "text",
    "userRequest": "帮我整理重点和待办",
    "profileContext": "用户喜欢温和、具体、低压力、不要太鸡血的建议。"
  }'

# 期望：返回 organize 结果 + savedMemories
# 随后 GET /api/memories 应能看到保存的记忆
```

---

## Phase 5：Chat 接口

### 目标

实现 `POST /api/chat` 全链路，使用 Chatflow 模式调用 Dify。

### 5.1 扩展 BelongAiService

在 `BelongAiService` 中添加 `processChat` 方法：

```java
public BelongResponse processChat(String message, String profileContext, String recentContext) {
    String userId = belongProperties.getDemoUserId();
    String memoryContext = memoryService.buildMemoryContext(userId);

    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("memory_context", memoryContext);
    inputs.put("profile_context", profileContext);
    inputs.put("recent_context", recentContext);

    String apiKey = belongProperties.getDify().getChatApiKey();
    JsonNode chatResult = difyService.callChatflow(apiKey, inputs, message, userId);

    List<Memory> savedMemories = extractAndSaveMemories(chatResult, userId);

    BelongResponse response = new BelongResponse();
    response.setResult(chatResult);
    response.setSavedMemories(new ArrayList<>(savedMemories));
    return response;
}
```

### 5.2 创建 ChatController

**文件**：`src/main/java/com/example/belong/controller/ChatController.java`

```java
package com.example.belong.controller;

import com.example.belong.dto.BelongResponse;
import com.example.belong.dto.ChatRequest;
import com.example.belong.service.BelongAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final BelongAiService belongAiService;

    @PostMapping("/chat")
    public BelongResponse chat(@RequestBody ChatRequest request) {
        return belongAiService.processChat(
                request.getMessage(),
                request.getProfileContext(),
                request.getRecentContext()
        );
    }
}
```

### 验证标准

先执行一次 Organize 存入记忆，然后：

```bash
curl -X POST http://localhost:8090/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "我今天不太想学了，感觉计划好多，有点烦。",
    "profileContext": "用户喜欢温和、具体、低压力、不要太鸡血的建议。",
    "recentContext": ""
  }'

# 期望：Chat 回复自然提到考研/时间/低压力上下文
# 不出现"根据记忆库"字样
# 返回 memory_event 和 savedMemories
```

---

## Phase 6：Plan 接口

### 目标

实现 `POST /api/plan` 全链路。

### 6.1 扩展 BelongAiService

在 `BelongAiService` 中添加 `processPlan` 方法：

```java
public BelongResponse processPlan(String planAction, String userGoal,
                                   String currentPlan, String progressContext,
                                   String profileContext) {
    String userId = belongProperties.getDemoUserId();
    String memoryContext = memoryService.buildMemoryContext(userId);

    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("plan_action", planAction);
    inputs.put("user_goal", userGoal);
    inputs.put("current_plan", currentPlan != null ? currentPlan : "");
    inputs.put("progress_context", progressContext != null ? progressContext : "");
    inputs.put("memory_context", memoryContext);
    inputs.put("profile_context", profileContext);

    String apiKey = belongProperties.getDify().getPlanApiKey();
    JsonNode planResult = difyService.callWorkflow(apiKey, inputs, userId);

    List<Memory> savedMemories = extractAndSaveMemories(planResult, userId);

    BelongResponse response = new BelongResponse();
    response.setResult(planResult);
    response.setSavedMemories(new ArrayList<>(savedMemories));
    return response;
}
```

### 6.2 创建 PlanController

**文件**：`src/main/java/com/example/belong/controller/PlanController.java`

```java
package com.example.belong.controller;

import com.example.belong.dto.BelongResponse;
import com.example.belong.dto.PlanRequest;
import com.example.belong.service.BelongAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PlanController {

    private final BelongAiService belongAiService;

    @PostMapping("/plan")
    public BelongResponse plan(@RequestBody PlanRequest request) {
        return belongAiService.processPlan(
                request.getPlanAction(),
                request.getUserGoal(),
                request.getCurrentPlan(),
                request.getProgressContext(),
                request.getProfileContext()
        );
    }
}
```

### 验证标准

```bash
curl -X POST http://localhost:8090/api/plan \
  -H "Content-Type: application/json" \
  -d '{
    "planAction": "create",
    "userGoal": "帮我制定一个接下来7天的考研恢复学习计划。",
    "currentPlan": "",
    "progressContext": "",
    "profileContext": "用户喜欢温和、具体、低压力、不要太鸡血的建议。"
  }'

# 期望：计划使用了"每晚2小时"的约束
# 任务具体且不超过3个/天
# 返回 memory_event 和 savedMemories
```

---

## Phase 7：异常处理与边界情况

### 目标

统一的错误响应格式，关键异常场景不 crash。

### 7.1 创建全局异常处理器

**文件**：`src/main/java/com/example/belong/exception/GlobalExceptionHandler.java`

```java
package com.example.belong.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "bad_request",
                "message", e.getMessage()
        ));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, String>> handleTimeout(ResourceAccessException e) {
        log.error("Dify API timeout", e);
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of(
                "error", "dify_timeout",
                "message", "Dify API 调用超时，请稍后重试"
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "internal_error",
                "message", e.getMessage() != null ? e.getMessage() : "服务器内部错误"
        ));
    }
}
```

### 7.2 边界情况检查清单

| 场景 | 处理策略 | 位置 |
|------|----------|------|
| Dify API Key 未配置（为 null 或空） | RestTemplate 在请求时因无 Bearer token 导致 Dify 返回 401，全局异常处理返回 `dify_error` | DifyService |
| Dify API 返回非 2xx | RestTemplate 默认抛 `HttpClientErrorException` / `HttpServerErrorException`，应由全局异常处理捕获 | GlobalExceptionHandler |
| Dify output 不是合法 JSON | `parseDifyOutput` 返回 null，上层判断后返回空结果，不 crash | DifyService |
| Memory Extract 失败 | `extractAndSaveMemories` 内 catch 异常，log.error，返回空 List，不影响用户结果 | BelongAiService |
| 请求必填字段为空 | Controller 层手动校验或使用 `@Valid` + `@NotBlank` | Controller |
| MySQL 连接失败 | Spring Boot 启动失败（fast fail），不会运行到一半才报错 | - |
| 重复记忆 | `saveMemories` 内 `countByUserIdAndContent` 去重 | MemoryService |
| 空 content 记忆 | `saveMemories` 内 `isBlank` 检查跳过 | MemoryService |

### 7.3 添加 RestTemplate 超时配置

在 `DifyService` 中配置超时，防止无限等待：

```java
private final RestTemplate restTemplate;

public DifyService(BelongProperties belongProperties) {
    this.belongProperties = belongProperties;
    this.restTemplate = new RestTemplate();
    
    // 设置超时：连接 5s，读取 60s
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(60000);
    this.restTemplate.setRequestFactory(factory);
}
```

> 注意：如果 `DifyService` 使用 `@RequiredArgsConstructor`，需要改为手动构造 RestTemplate。

---

## Phase 8：联调验证

### 目标

按 MVP 验收测试用例，逐条验证全链路。

### 8.1 环境准备

```bash
# 确保 MySQL 运行中，belong 库和 memories 表已创建

# 设置环境变量（替换为真实 Key）
export BELONG_DIFY_CHAT_API_KEY="app-xxx"
export BELONG_DIFY_ORGANIZE_API_KEY="app-xxx"
export BELONG_DIFY_PLAN_API_KEY="app-xxx"
export BELONG_DIFY_MEMORY_API_KEY="app-xxx"
export BELONG_DB_USERNAME="root"
export BELONG_DB_PASSWORD="yourpassword"

# 启动
./mvnw spring-boot:run
```

### 8.2 测试用例

**Test 1: Health Check**

```bash
curl http://localhost:8090/api/health
# → {"status":"ok"}
```

**Test 2: Organize → Memories**

```bash
# Step 1: 发送整理请求
curl -X POST http://localhost:8090/api/organize \
  -H "Content-Type: application/json" \
  -d '{
    "content": "我最近决定开始准备2027年研究生考试，目标专业是计算机技术。白天上班，晚上大概只能稳定学习2小时。我喜欢明确的小任务，不喜欢太激进、太空泛的计划。",
    "contentType": "text",
    "userRequest": "帮我整理这份学习规划，提炼重点和接下来要做的事",
    "profileContext": "用户喜欢温和、具体、低压力、不要太鸡血的建议。"
  }'

# Step 2: 验证记忆已保存
curl http://localhost:8090/api/memories
# → 应包含考研相关记忆
```

**Test 3: Chat 使用记忆**

```bash
curl -X POST http://localhost:8090/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "我今天不太想学了，感觉计划好多，有点烦。",
    "profileContext": "用户喜欢温和、具体、低压力、不要太鸡血的建议。",
    "recentContext": ""
  }'
# → 回复自然引用考研/2小时/低压力等上下文
# → 不出现"根据记忆库"
# → 返回 memory_event
```

**Test 4: Plan 使用记忆**

```bash
curl -X POST http://localhost:8090/api/plan \
  -H "Content-Type: application/json" \
  -d '{
    "planAction": "create",
    "userGoal": "帮我制定一个接下来7天的考研恢复学习计划。",
    "currentPlan": "",
    "progressContext": "",
    "profileContext": "用户喜欢温和、具体、低压力、不要太鸡血的建议。"
  }'
# → 计划使用了"每晚2小时"约束
# → 任务具体且合理
# → 返回 memory_event 和 savedMemories
```

**Test 5: Memory Archive**

```bash
# 取一个存在的 memory id
curl -X DELETE http://localhost:8090/api/memories/1
# → {"success":true}

# 确认已归档（列表中不再出现）
curl http://localhost:8090/api/memories
```

---

## 附录：文件清单汇总

### 新增文件一览

```
src/main/java/com/example/belong/
├── config/
│   └── BelongProperties.java          ← Phase 1
├── controller/
│   ├── HealthController.java          ← Phase 1
│   ├── MemoryController.java          ← Phase 2
│   ├── ChatController.java            ← Phase 5
│   ├── OrganizeController.java        ← Phase 4
│   └── PlanController.java            ← Phase 6
├── dto/
│   ├── ChatRequest.java               ← Phase 3
│   ├── OrganizeRequest.java           ← Phase 3
│   ├── PlanRequest.java               ← Phase 3
│   └── BelongResponse.java            ← Phase 3
├── entity/
│   └── Memory.java                    ← Phase 2
├── mapper/
│   └── MemoryMapper.java              ← Phase 2
├── service/
│   ├── DifyService.java               ← Phase 3
│   ├── MemoryService.java             ← Phase 2
│   └── BelongAiService.java           ← Phase 4（后续 Phase 5/6 扩展）
└── exception/
    └── GlobalExceptionHandler.java    ← Phase 7

src/main/resources/
└── application.yaml                   ← Phase 1（修改已有文件）
```

### 修改文件一览

| 文件 | Phase | 修改内容 |
|------|-------|----------|
| `application.yaml` | 1 | 补全端口、数据源、MyBatis、Dify 配置 |
| `BelongAiService.java` | 5, 6 | 添加 `processChat`、`processPlan` 方法 |

### 数据库

```sql
-- Phase 1 执行
CREATE DATABASE IF NOT EXISTS belong DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE TABLE memories ( ... );  -- 见 Phase 1.4
```

---

## 开发注意事项

1. **不硬编码 API Key**：所有 Dify Key 通过环境变量注入，配置文件中仅引用 `${...}` 占位符。
2. **V1 使用 demo-user**：所有接口暂时 hardcode `demo-user` 作为 userId，来源为 `BelongProperties.demoUserId`。
3. **Memory Extract 不阻断主流程**：即使用户对话/整理/计划成功，Memory Extract 失败也只记日志、返回空 `savedMemories`，不影响用户看到的回复。
4. **去重策略**：V1 使用完全字符串匹配（`content = ?`），未来可升级为模糊去重或向量去重。
5. **记忆上下文**：V1 简单取最近 20 条拼接，不涉及向量搜索、关键词匹配或类型过滤。
6. **RestTemplate 而非 WebClient**：V1 简单直接，不引入额外依赖；后续若需流式响应（SSE）再升级为 WebClient。
7. **Dify 输出解析**：DifyService 的 `parseDifyOutput` 是兼容性关键点，需根据实际 Dify flow 的输出结构调整。文档中的解析逻辑覆盖了常见情况，但联调时可能需要微调。
