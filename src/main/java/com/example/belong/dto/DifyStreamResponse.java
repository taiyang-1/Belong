package com.example.belong.dto;

import java.io.InputStream;
import java.net.HttpURLConnection;

public record DifyStreamResponse(HttpURLConnection connection, InputStream body) implements AutoCloseable {

    @Override
    public void close() {
        try {
            body.close();
        } catch (Exception ignored) {
        }
        connection.disconnect();
    }
}
