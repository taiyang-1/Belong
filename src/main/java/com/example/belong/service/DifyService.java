package com.example.belong.service;

import com.example.belong.config.BelongProperties;
import com.example.belong.dto.DifyStreamResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
public class DifyService {

    private final BelongProperties belongProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DifyService(BelongProperties belongProperties) {
        this.belongProperties = belongProperties;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(belongProperties.getDify().getConnectTimeoutMs());
        factory.setReadTimeout(belongProperties.getDify().getReadTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Call Chatflow (Chat scenario).
     */
    public JsonNode callChatflow(String apiKey, Map<String, Object> inputs, String query, String user) {
        String url = belongProperties.getDify().getBaseUrl() + "/chat-messages";

        Map<String, Object> body = Map.of(
                "inputs", (Object) inputs,
                "query", query,
                "response_mode", "blocking",
                "user", user
        );

        ResponseEntity<String> response = postJson(url, apiKey, body);
        return parseDifyOutput(response.getBody());
    }

    public DifyStreamResponse openChatflowStream(String apiKey, Map<String, Object> inputs,
                                                 String query, String user) {
        String url = belongProperties.getDify().getBaseUrl() + "/chat-messages";
        Map<String, Object> body = Map.of(
                "inputs", (Object) inputs,
                "query", query,
                "response_mode", "streaming",
                "user", user
        );

        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(belongProperties.getDify().getConnectTimeoutMs());
            connection.setReadTimeout(belongProperties.getDify().getReadTimeoutMs());
            connection.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            connection.setRequestProperty("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            byte[] payload = objectMapper.writeValueAsBytes(body);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }

            int status = connection.getResponseCode();
            InputStream bodyStream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (status >= 400) {
                String errorBody = bodyStream == null
                        ? ""
                        : new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
                connection.disconnect();
                throw new IllegalStateException("Dify API 返回错误: " + status + " " + errorBody);
            }
            return new DifyStreamResponse(connection, bodyStream);
        } catch (Exception e) {
            throw new IllegalStateException("Dify stream request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Call Workflow (Organize / Plan / Memory Extract scenarios).
     */
    public JsonNode callWorkflow(String apiKey, Map<String, Object> inputs, String user) {
        String url = belongProperties.getDify().getBaseUrl() + "/workflows/run";

        Map<String, Object> body = Map.of(
                "inputs", (Object) inputs,
                "response_mode", "blocking",
                "user", user
        );

        ResponseEntity<String> response = postJson(url, apiKey, body);
        return parseDifyOutput(response.getBody());
    }

    private ResponseEntity<String> postJson(String url, String apiKey, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    /**
     * Parse Dify JSON response.
     * Compatible with two formats:
     *   1. {"data": {"outputs": {"xxx": "{...}"}}}  → Workflow
     *   2. {"answer": "{...}"}                       → Chatflow
     *
     * If the output value is a JSON string, parse it into JsonNode;
     * if it's already a JSON object, use it directly.
     */
    public JsonNode parseDifyOutput(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Chatflow format: extract from answer field
            if (root.has("answer")) {
                return parseJsonValue(root.get("answer"));
            }

            // Workflow format: first value under data.outputs
            if (root.has("data") && root.get("data").has("outputs")) {
                JsonNode outputs = root.get("data").get("outputs");
                var fields = outputs.fields();
                if (fields.hasNext()) {
                    JsonNode value = fields.next().getValue();
                    return parseJsonValue(value);
                }
            }

            // Fallback: return the entire response
            return root;
        } catch (Exception e) {
            log.error("Failed to parse Dify output: {}", responseBody, e);
            return null;
        }
    }

    private JsonNode parseJsonValue(JsonNode node) throws Exception {
        if (node.isObject() || node.isArray()) {
            return node;
        }
        // Text value: try to extract JSON substring
        String text = node.asText().trim();
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return objectMapper.readTree(text.substring(start, end + 1));
        }
        return node;
    }
}
