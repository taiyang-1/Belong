package com.example.belong.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Memory {
    private Long id;
    private String userId;
    private String type;
    private String content;
    private BigDecimal confidence;
    private String sensitivity;
    private LocalDateTime expiresAt;
    private String source;
    private String reason;
    private Boolean isArchived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
