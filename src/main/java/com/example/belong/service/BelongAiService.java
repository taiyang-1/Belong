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
     * Organize flow:
     *   1. Read active memories → build memory_context
     *   2. Call Organize Workflow
     *   3. Extract memory_event from result
     *   4. Call Memory Extract workflow
     *   5. Save extracted memories
     *   6. Return BelongResponse
     */
    public BelongResponse processOrganize(String content, String contentType,
                                          String userRequest, String profileContext) {
        String userId = belongProperties.getDemoUserId();
        String memoryContext = memoryService.buildMemoryContext(userId);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("content", content);
        inputs.put("content_type", contentType);
        inputs.put("user_request", userRequest);
        inputs.put("memory_context", memoryContext);
        inputs.put("profile_context", profileContext);

        String apiKey = belongProperties.getDify().getOrganizeApiKey();
        JsonNode organizeResult = difyService.callWorkflow(apiKey, inputs, userId);

        List<Memory> savedMemories = extractAndSaveMemories(organizeResult, userId);

        BelongResponse response = new BelongResponse();
        response.setResult(organizeResult);
        response.setSavedMemories(new ArrayList<>(savedMemories));
        return response;
    }

    /**
     * Chat flow: see processChat below.
     */
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

    /**
     * Plan flow: see processPlan below.
     */
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

    /**
     * Extract memory_event from Dify result, call Memory Extract, and save memories.
     * If memory extraction fails, log and return empty list — never block user-facing flow.
     */
    private List<Memory> extractAndSaveMemories(JsonNode flowResult, String userId) {
        try {
            JsonNode memoryEvent = flowResult != null ? flowResult.get("memory_event") : null;
            if (memoryEvent == null || memoryEvent.isNull()) {
                return List.of();
            }

            String memorySnapshot = memoryService.buildMemoryContext(userId);

            Map<String, Object> extractInputs = new LinkedHashMap<>();
            extractInputs.put("memory_event", memoryEvent.toString());
            extractInputs.put("memory_snapshot", memorySnapshot);

            String memoryApiKey = belongProperties.getDify().getMemoryApiKey();
            JsonNode extractResult = difyService.callWorkflow(memoryApiKey, extractInputs, userId);

            if (extractResult == null) {
                log.warn("Memory Extract returned null for user {}", userId);
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
                m.setConfidence(node.has("confidence")
                        ? BigDecimal.valueOf(node.get("confidence").asDouble()) : null);
                m.setSensitivity(node.has("sensitivity")
                        ? node.get("sensitivity").asText() : "medium");
                m.setSource(memoryEvent.has("source")
                        ? memoryEvent.get("source").asText() : null);
                m.setReason(node.has("reason")
                        ? node.get("reason").asText() : null);
                m.setIsArchived(false);

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
