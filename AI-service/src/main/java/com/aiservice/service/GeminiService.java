package com.aiservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Service
@Slf4j
public class GeminiService {

    private final WebClient webClient;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.api-key}")
    private String geminiApiKey;

    public GeminiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String getResponseFromGemini(String prompt) {
        // Build a request body using the "messages" schema the Generative Language API expects.
        Map<String, Object> requestBody = Map.of(
                "contents", new Object[] {
                        Map.of("parts", new Object[] {
                                Map.of("text", prompt)
                        })
                });

        String response = null;
        try {
            response = webClient.post()
                    .uri(geminiApiUrl)
                    .header("X-goog-api-key", geminiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            resp -> resp.bodyToMono(String.class)
                                    .flatMap(error -> {
                                        log.error("GEMINI ERROR = {}", error);
                                        return Mono.error(new RuntimeException(error));
                                    })
                    )
                    .bodyToMono(String.class)
                    .block();

            if (response == null) {
                log.error("Gemini returned null response body for prompt (truncated): {}", prompt.length() > 200 ? prompt.substring(0,200) + "..." : prompt);
            } else {
                String truncated = response.length() > 1000 ? response.substring(0,1000) + "..." : response;
                log.debug("Gemini response (truncated): {}", truncated);
            }
        } catch (Exception e) {
            log.error("Error calling Gemini API: {}", e.getMessage(), e);
            throw e;
        }

        return response;
    }
}
