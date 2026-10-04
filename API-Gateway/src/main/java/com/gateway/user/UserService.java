package com.gateway.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final WebClient userServiceWebClient;

    public Mono<Boolean> validateUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return Mono.just(false);
        }

        log.info("Calling User Validation API for userId: {}", userId);
        return userServiceWebClient.get()
                .uri("/api/users/{userId}/validate", userId)
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                        log.warn("User validation failed for userId {}: {}", userId, e.getStatusCode());
                        return Mono.just(false);
                    }
                    return Mono.error(e);
                });
    }

    public Mono<UserResponse> registerUser(RegisterRequest registerRequest) {
        log.info("Calling User Registration API for email: {}", registerRequest.getEmail());

        try{
            return userServiceWebClient.post()
                    .uri("/api/users/register")
                    .bodyValue(registerRequest)
                    .retrieve()
                    .bodyToMono(UserResponse.class)
                    .onErrorResume(WebClientResponseException.class, e -> {
                        if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                            return Mono.error(new RuntimeException("Invalid Request: " + e.getMessage()));
                        } else if (e.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
                            return Mono.error(new RuntimeException("User Already Exists: " + e.getMessage()));
                        } else {
                            return Mono.error(e);
                        }
                    });
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                return Mono.error(new RuntimeException("Invalid Request: " + e.getMessage()));
            } else if (e.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
                return Mono.error(new RuntimeException("User Already Exists: " + e.getMessage()));
            } else {
                return Mono.error(e);
            }
        }
    }

}
