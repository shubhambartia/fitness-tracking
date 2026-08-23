package com.aiservice.service;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private final WebClient webClient;
    private final RateLimiter rateLimiter;

    @Value("${gemini.api.api-key}")
    private String geminiApiKey;

    public GeminiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("X-goog-api-key", geminiApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        // Allow 1 request per second, with a burst of 5
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(1)
                .timeoutDuration(Duration.ofSeconds(2))
                .build();
        this.rateLimiter = RateLimiter.of("geminiLimiter", config);
    }

    public String getAnswer(String question) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", question)
                        ))
                )
        );

        try {
            return RateLimiter.decorateSupplier(rateLimiter, () ->
                    webClient.post()
                            .uri("/models/gemini-flash-latest:generateContent")
                            .bodyValue(requestBody)
                            .retrieve()
                            .onStatus(HttpStatus.TOO_MANY_REQUESTS::equals,
                                    response -> Mono.error(new RuntimeException("Rate limit exceeded")))
                            .bodyToMono(String.class)
                            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                                    .maxBackoff(Duration.ofSeconds(30))
                                    .jitter(0.5))
                            .block()
            ).get();
        } catch (Exception e) {
            // Graceful fallback
            return "{\"analysis\":{\"overall\":\"Unable to generate due to rate limit\"}}";
        }
    }
}
