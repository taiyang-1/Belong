package com.example.belong.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatResultExtractor {

    private final ObjectMapper objectMapper;

    public JsonNode fromJsonOnlyPayload(String payload) {
        JsonNode json = parseJsonFromText(payload);
        if (json != null && json.isObject()) {
            return normalizeResult("", json);
        }
        return normalizeResult(payload, null);
    }

    public JsonNode fromVisibleReplyAndMetaPayload(String reply, String metaPayload) {
        return normalizeResult(reply, parseJsonFromText(metaPayload));
    }

    private JsonNode normalizeResult(String fallbackReply, JsonNode metadata) {
        ObjectNode result = objectMapper.createObjectNode();
        String reply = fallbackReply == null ? "" : fallbackReply.strip();

        if (metadata != null && metadata.isObject()) {
            JsonNode replyNode = metadata.get("reply");
            if ((reply == null || reply.isBlank()) && replyNode != null && replyNode.isTextual()) {
                reply = replyNode.asText("").strip();
            }

            JsonNode suggestedActions = metadata.get("suggested_actions");
            if (suggestedActions != null && suggestedActions.isArray()) {
                result.set("suggested_actions", suggestedActions);
            }

            JsonNode memoryEvent = metadata.get("memory_event");
            if (memoryEvent != null && !memoryEvent.isNull()) {
                result.set("memory_event", memoryEvent);
            }
        }

        result.put("reply", reply == null ? "" : reply);
        return result;
    }

    private JsonNode parseJsonFromText(String text) {
        try {
            String trimmed = text == null ? "" : text.trim();
            if (trimmed.isBlank()) {
                return null;
            }
            int start = trimmed.indexOf("{");
            int end = trimmed.lastIndexOf("}");
            if (start < 0 || end <= start) {
                return null;
            }
            return objectMapper.readTree(trimmed.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }
}
