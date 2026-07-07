package com.example.belong.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatConversationSummary {
    private String conversationId;
    private String title;
    private String lastMessage;
    private LocalDateTime updatedAt;
    private Integer messageCount;
}
