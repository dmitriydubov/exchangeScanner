package com.exchange.scanner.security.dto.request;

public record PasswordResetRequest(String username, String email, String password, String confirmPassword) {
}
