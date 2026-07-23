package com.example.belong.controller;

import com.example.belong.dto.BelongResponse;
import com.example.belong.dto.ChatRequest;
import com.example.belong.dto.ChatConversationSummary;
import com.example.belong.dto.ChatStreamContext;
import com.example.belong.dto.DifyStreamResponse;
import com.example.belong.entity.ChatMessage;
import com.example.belong.config.BelongProperties;
import com.example.belong.service.BelongAiService;
import com.example.belong.service.ChatMessageService;
import com.example.belong.service.ChatResultExtractor;
import com.example.belong.service.DifyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private static final String META_DELIMITER = "[BELONG_META]";

    private final BelongProperties belongProperties;
    private final BelongAiService belongAiService;
    private final ChatMessageService chatMessageService;
    private final ChatResultExtractor chatResultExtractor;
    private final DifyService difyService;
    private final ObjectMapper objectMapper;

    @PostMapping("/chat")
    public BelongResponse chat(@RequestBody ChatRequest request) {
        return belongAiService.processChat(
                request.getConversationId(),
                request.getMessage(),
                request.getProfileContext(),
                request.getRecentContext()
        );
    }

    @PostMapping("/chat/stream")
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(belongProperties.getDify().getReadTimeoutMs() + 5_000L);
        Thread.startVirtualThread(() -> pipeDifyChatStream(request, emitter));
        return emitter;
    }

    @GetMapping("/chat/messages")
    public List<ChatMessage> latestMessages(@RequestParam(required = false) Integer limit) {
        return chatMessageService.getLatestMessages(belongProperties.getDemoUserId(), limit);
    }

    @GetMapping("/chat/conversations")
    public List<ChatConversationSummary> latestConversations(@RequestParam(required = false) Integer limit) {
        return chatMessageService.getConversationSummaries(belongProperties.getDemoUserId(), limit);
    }

    @GetMapping("/chat/conversations/{conversationId}/messages")
    public List<ChatMessage> conversationMessages(@PathVariable String conversationId) {
        return chatMessageService.getConversationMessages(belongProperties.getDemoUserId(), conversationId);
    }

    @DeleteMapping("/chat/conversations/{conversationId}")
    public void deleteConversation(@PathVariable String conversationId) {
        chatMessageService.deleteConversation(belongProperties.getDemoUserId(), conversationId);
    }

    private void pipeDifyChatStream(ChatRequest request, SseEmitter emitter) {
        ChatStreamContext context = belongAiService.buildStreamChatContext(
                request.getConversationId(),
                request.getMessage(),
                request.getProfileContext(),
                request.getRecentContext()
        );
        StringBuilder accumulatedText = new StringBuilder();
        StringBuilder metaPart = new StringBuilder();
        int sentLength = 0;
        boolean separatorFound = false;
        boolean jsonOnlyPayload = false;
        boolean replyEmitted = false;

        try {
            chatMessageService.saveMessage(context.getUserId(), context.getConversationId(), "user", context.getMessage(), null);

            try (DifyStreamResponse response = difyService.openChatflowStream(
                    context.getApiKey(),
                    context.getInputs(),
                    context.getMessage(),
                    context.getUserId()
            );
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }

                    JsonNode event = parseDifyStreamEvent(line.substring("data:".length()).trim());
                    if (event == null) {
                        continue;
                    }

                    String eventName = event.has("event") ? event.get("event").asText() : "";
                    if ("error".equals(eventName)) {
                        String message = event.has("message") ? event.get("message").asText() : "Dify API 返回错误";
                        emitter.send(SseEmitter.event().name("error").data(message));
                        emitter.complete();
                        return;
                    }
                    if ("message_end".equals(eventName)) {
                        break;
                    }
                    if (!"message".equals(eventName) || !event.has("answer")) {
                        continue;
                    }

                    String answer = event.get("answer").asText("");
                    accumulatedText.append(answer);
                    jsonOnlyPayload = jsonOnlyPayload || startsWithJsonPayload(accumulatedText.toString());

                    if (separatorFound) {
                        metaPart.append(answer);
                        continue;
                    }

                    int delimiterIndex = accumulatedText.indexOf(META_DELIMITER);
                    if (delimiterIndex >= 0) {
                        String replyPart = accumulatedText.substring(0, delimiterIndex);
                        replyEmitted = sendDelta(emitter, replyPart, sentLength) || replyEmitted;
                        sentLength = replyPart.length();
                        metaPart.append(accumulatedText.substring(delimiterIndex + META_DELIMITER.length()));
                        separatorFound = true;
                        continue;
                    }

                    if (jsonOnlyPayload) {
                        continue;
                    }

                    int safeLength = safeStreamingLength(accumulatedText.toString());
                    replyEmitted = sendDelta(emitter, accumulatedText.toString(), sentLength, safeLength) || replyEmitted;
                    sentLength = safeLength;
                }
            }

            JsonNode chatResult = separatorFound
                    ? chatResultExtractor.fromVisibleReplyAndMetaPayload(
                            accumulatedText.substring(0, accumulatedText.indexOf(META_DELIMITER)),
                            metaPart.toString()
                    )
                    : chatResultExtractor.fromJsonOnlyPayload(accumulatedText.toString());
            String finalReply = chatResult.has("reply") ? chatResult.get("reply").asText("") : "";
            if (!replyEmitted && !finalReply.isBlank()) {
                emitter.send(SseEmitter.event().name("message").data(finalReply));
            }
            belongAiService.saveAssistantChatMessage(context.getUserId(), context.getConversationId(), chatResult);
            var savedMemories = belongAiService.extractAndSaveMemories(chatResult, context.getUserId());

            BelongResponse done = new BelongResponse();
            done.setResult(chatResult);
            done.setSavedMemories(new ArrayList<>(savedMemories));
            emitter.send(SseEmitter.event().name("done").data(done));
            emitter.complete();
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
            } catch (Exception ignored) {
            }
            emitter.completeWithError(e);
        }
    }

    private JsonNode parseDifyStreamEvent(String data) {
        try {
            if (data == null || data.isBlank() || "[DONE]".equals(data)) {
                return null;
            }
            return objectMapper.readTree(data);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean sendDelta(SseEmitter emitter, String text, int fromIndex) throws Exception {
        return sendDelta(emitter, text, fromIndex, text.length());
    }

    private boolean sendDelta(SseEmitter emitter, String text, int fromIndex, int toIndex) throws Exception {
        if (toIndex <= fromIndex) {
            return false;
        }

        String delta = text.substring(fromIndex, toIndex);
        if (!delta.isBlank()) {
            emitter.send(SseEmitter.event().name("message").data(delta));
            return true;
        }
        return false;
    }

    private int safeStreamingLength(String text) {
        for (int length = META_DELIMITER.length() - 1; length > 0; length--) {
            if (text.endsWith(META_DELIMITER.substring(0, length))) {
                return text.length() - length;
            }
        }
        return text.length();
    }

    private boolean startsWithJsonPayload(String text) {
        String trimmed = text == null ? "" : text.stripLeading();
        return trimmed.startsWith("{");
    }
}
