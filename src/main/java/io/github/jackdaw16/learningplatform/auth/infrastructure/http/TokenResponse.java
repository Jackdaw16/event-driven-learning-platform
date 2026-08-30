package io.github.jackdaw16.learningplatform.auth.infrastructure.http;

public record TokenResponse(String tokenType, String accessToken, long expiresInSeconds) {
}
