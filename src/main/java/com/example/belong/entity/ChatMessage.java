package com.example.belong.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessage {
    private Long id;
    private String userId;
    private String conversationId;
    private String role;
    private String content;
    private String suggestedActions;
    private LocalDateTime createdAt;
}
