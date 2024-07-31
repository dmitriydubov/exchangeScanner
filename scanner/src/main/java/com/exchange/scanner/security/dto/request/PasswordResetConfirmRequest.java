package com.exchange.scanner.security.dto.request;

public record PasswordResetConfirmRequest(PasswordResetRequest passwordResetRequest, String code) {
}
