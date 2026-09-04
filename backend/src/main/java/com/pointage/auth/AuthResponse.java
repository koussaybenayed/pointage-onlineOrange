package com.pointage.auth;

public record AuthResponse(String accessToken, String refreshToken, Long userId, String email, String fullName, String role, String team) {
}
