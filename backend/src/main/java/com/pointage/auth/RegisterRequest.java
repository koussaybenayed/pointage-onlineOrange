package com.pointage.auth;

public record RegisterRequest(String fullName, String email, String password, boolean asAdmin, String team) {
}
