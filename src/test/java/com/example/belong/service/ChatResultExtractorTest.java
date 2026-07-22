package com.example.belong.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatResultExtractorTest {

    private final ChatResultExtractor extractor = new ChatResultExtractor(new ObjectMapper());

    @Test
    void extractsReplyAndMetadataFromJsonOnlyPayload() {
        JsonNode result = extractor.fromJsonOnlyPayload("""
                {
                  "reply": "你好呀！今天过得怎么样？",
                  "suggested_actions": ["说说今天的心情", "分享一件小事"],
                  "memory_event": {
                    "source": "chat",
                    "action": "user_chat",
                    "summary": "用户打招呼，Belong 用中文回应并开启话题。",
                    "important_points": []
                  }
                }
                """);

        assertThat(result.get("reply").asText()).isEqualTo("你好呀！今天过得怎么样？");
        assertThat(result.get("suggested_actions")).hasSize(2);
        assertThat(result.get("memory_event").get("summary").asText())
                .isEqualTo("用户打招呼，Belong 用中文回应并开启话题。");
    }

    @Test
    void buildsResultFromVisibleReplyAndMetaPayload() {
        JsonNode result = extractor.fromVisibleReplyAndMetaPayload(
                "嗨，我在。",
                """
                        {"suggested_actions":["继续聊"],"memory_event":{"source":"chat","action":"user_chat","summary":"用户打招呼。","important_points":[]}}
                        """
        );

        assertThat(result.get("reply").asText()).isEqualTo("嗨，我在。");
        assertThat(result.get("suggested_actions")).hasSize(1);
        assertThat(result.get("memory_event").get("source").asText()).isEqualTo("chat");
    }
}
