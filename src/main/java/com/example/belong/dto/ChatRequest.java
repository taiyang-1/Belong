package com.example.belong.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String profileContext;
    private String recentContext;
}
