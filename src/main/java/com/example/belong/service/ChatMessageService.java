package com.example.belong.service;

import com.example.belong.dto.ChatConversationSummary;
import com.example.belong.entity.ChatMessage;
import com.example.belong.mapper.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;

    public List<ChatMessage> getLatestMessages(String userId, Integer limit) {
        List<ChatMessage> latest = new ArrayList<>(chatMessageMapper.findLatestByUserId(userId, normalizeLimit(limit)));
        Collections.reverse(latest);
        return latest;
    }

    public List<ChatConversationSummary> getConversationSummaries(String userId, Integer limit) {
        return chatMessageMapper.findRecentConversationsByUserId(userId, normalizeLimit(limit));
    }

    public List<ChatMessage> getConversationMessages(String userId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        return chatMessageMapper.findByUserIdAndConversationId(userId, conversationId);
    }

    public int deleteConversation(String userId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return 0;
        }
        return chatMessageMapper.deleteByUserIdAndConversationId(userId, conversationId);
    }

    public String buildRecentContext(String userId, Integer limit) {
        List<ChatMessage> latest = getLatestMessages(userId, limit);
        if (latest.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : latest) {
            String speaker = "assistant".equals(message.getRole()) ? "Belong" : "用户";
            sb.append(speaker).append("：").append(message.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    public ChatMessage saveMessage(String userId, String conversationId, String role,
                                   String content, String suggestedActions) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId(userId);
        chatMessage.setConversationId(conversationId);
        chatMessage.setRole(role);
        chatMessage.setContent(content);
        chatMessage.setSuggestedActions(suggestedActions);
        chatMessageMapper.insert(chatMessage);
        return chatMessage;
    }

    int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 20;
        }
        return Math.max(1, Math.min(limit, 20));
    }
}
