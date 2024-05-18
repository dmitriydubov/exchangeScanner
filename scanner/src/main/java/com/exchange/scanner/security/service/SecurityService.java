package com.exchange.scanner.security.service;

import com.exchange.scanner.security.dto.request.RequestTokenRefresh;
import com.exchange.scanner.security.dto.request.SignInRequest;
import com.exchange.scanner.security.dto.request.SignUpRequest;
import com.exchange.scanner.security.dto.response.LoginResponse;
import com.exchange.scanner.security.dto.response.RefreshTokenResponse;

public interface SecurityService {
    LoginResponse authenticateUser(SignInRequest signInRequest);
    void register(SignUpRequest signUpRequest);
    RefreshTokenResponse refreshToken(RequestTokenRefresh request);
    void logout();
}
