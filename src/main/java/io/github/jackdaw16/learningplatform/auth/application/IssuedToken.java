package io.github.jackdaw16.learningplatform.auth.application;

public record IssuedToken(String tokenType, String accessToken, long expiresInSeconds) {
}
