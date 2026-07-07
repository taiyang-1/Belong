package com.example.belong.controller;

import com.example.belong.dto.BelongResponse;
import com.example.belong.dto.ChatRequest;
import com.example.belong.dto.ChatConversationSummary;
import com.example.belong.entity.ChatMessage;
import com.example.belong.config.BelongProperties;
import com.example.belong.service.BelongAiService;
import com.example.belong.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final BelongProperties belongProperties;
    private final BelongAiService belongAiService;
    private final ChatMessageService chatMessageService;

    @PostMapping("/chat")
    public BelongResponse chat(@RequestBody ChatRequest request) {
        return belongAiService.processChat(
                request.getConversationId(),
                request.getMessage(),
                request.getProfileContext(),
                request.getRecentContext()
        );
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
}
