package com.exchange.scanner.security.service;

import com.exchange.scanner.dto.response.SimpleResponse;
import com.exchange.scanner.security.dto.request.*;
import com.exchange.scanner.security.dto.response.LoginResponse;
import com.exchange.scanner.security.dto.response.PasswordResetResponse;
import com.exchange.scanner.security.dto.response.RefreshTokenResponse;
import com.exchange.scanner.security.dto.response.RegisterResponse;

public interface SecurityService {
    LoginResponse authenticateUser(SignInRequest signInRequest);
    RegisterResponse register(SignUpRequest signUpRequest);
    RefreshTokenResponse refreshToken(RequestTokenRefresh request);
    void logout();
    PasswordResetResponse getPasswordResetConfirmationCode(PasswordResetRequest passwordResetRequest);

    SimpleResponse confirmResetCode(PasswordResetConfirmRequest passwordResetConfirmRequest);
}
