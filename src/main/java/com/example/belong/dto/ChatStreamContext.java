package com.example.belong.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class ChatStreamContext {
    private String userId;
    private String conversationId;
    private String message;
    private Map<String, Object> inputs;
    private String apiKey;
}
