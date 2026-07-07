package com.example.belong.dto;

import lombok.Data;

@Data
public class OrganizeRequest {
    private String content;
    private String contentType;
    private String userRequest;
    private String profileContext;
}
