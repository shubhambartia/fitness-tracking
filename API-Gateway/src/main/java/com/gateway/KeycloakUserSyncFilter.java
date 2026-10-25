package com.gateway;

import com.gateway.user.RegisterRequest;
import com.gateway.user.UserService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserSyncFilter implements WebFilter {

    private final UserService userService;

    @Override
    public Mono<Void> filter(ServerWebExchange serverWebExchange, WebFilterChain webFilterChain) {
        String userId = serverWebExchange.getRequest().getHeaders().getFirst("X-User-Id");
        String token = serverWebExchange.getRequest().getHeaders().getFirst("Authorization");
        RegisterRequest registerRequest = getUserDetailsFromToken(token);

        if (registerRequest != null && registerRequest.getKeyCloakId() != null) {
            String tokenUserId = registerRequest.getKeyCloakId();
            if (userId == null || !userId.equals(tokenUserId)) {
                log.info("Using Keycloak user ID from token: {}", tokenUserId);
                userId = tokenUserId;
            }
        }

        if (userId != null && token != null && isBearerToken(token)) {
            log.info("Validating user with ID: {}", userId);

            String finalUserId = userId;
            return userService.validateUser(finalUserId)
                    .flatMap(isValid -> {
                        if (!isValid) {
                            log.warn("User with ID: {} is not valid", finalUserId);
                            if (registerRequest != null && registerRequest.getKeyCloakId() != null) {
                                return userService.registerUser(registerRequest)
                                        .then(proceedWithRequest(serverWebExchange, webFilterChain, finalUserId));
                            }
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.UNAUTHORIZED,
                                    "User with ID: " + finalUserId + " is not valid"));
                        }

                        log.info("User with ID: {} is valid", finalUserId);
                        return proceedWithRequest(serverWebExchange, webFilterChain, finalUserId);
                    });
        }

        return webFilterChain.filter(serverWebExchange);
    }

    private Mono<Void> proceedWithRequest(ServerWebExchange serverWebExchange,
                                         WebFilterChain webFilterChain,
                                         String userId) {
        ServerHttpRequest mutatedRequest = serverWebExchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .build();

        return webFilterChain.filter(serverWebExchange.mutate().request(mutatedRequest).build());
    }

    private boolean isBearerToken(String token) {
        return token != null && token.trim().length() > 7 && token.regionMatches(true, 0, "Bearer ", 0, 7);
    }

    private RegisterRequest getUserDetailsFromToken(String token) {
        if (!isBearerToken(token)) {
            return null;
        }

        try {
            String tokenWithoutBearer = token.replaceFirst("(?i)^Bearer\\s+", "").trim();
            SignedJWT signedJWT = SignedJWT.parse(tokenWithoutBearer);
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();

            RegisterRequest registerRequest = new RegisterRequest();
            registerRequest.setKeyCloakId(claimsSet.getStringClaim("sub"));
            registerRequest.setEmail(claimsSet.getStringClaim("email"));
            registerRequest.setFirstName(claimsSet.getStringClaim("given_name"));
            registerRequest.setLastName(claimsSet.getStringClaim("family_name"));
            registerRequest.setPassword("dummy@123");

            return registerRequest;
        } catch (Exception e) {
            log.warn("Unable to parse the bearer token for user sync.", e);
            return null;
        }
    }
}
