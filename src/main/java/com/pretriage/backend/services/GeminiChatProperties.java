package com.pretriage.backend.services;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "pretriage.ia.gemini")
public record GeminiChatProperties(String apiKey, String model, String baseUrl,
                                   Duration connectTimeout, Duration requestTimeout) {
    public GeminiChatProperties {
        if (model == null || model.isBlank()) model = "gemini-3.5-flash-lite";
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(5);
        if (requestTimeout == null) requestTimeout = Duration.ofSeconds(45);
    }
}
