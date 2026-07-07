package com.example.belong.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "belong")
public class BelongProperties {

    private String demoUserId;
    private Dify dify = new Dify();

    @Data
    public static class Dify {
        private String baseUrl;
        private String chatApiKey;
        private String organizeApiKey;
        private String planApiKey;
        private String memoryApiKey;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 180000;
    }
}
