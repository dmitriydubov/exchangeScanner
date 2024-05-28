package com.exchange.scanner.security.controller;

import com.exchange.scanner.security.dto.response.RegisterResponse;
import com.exchange.scanner.security.error.UserAlreadyExistException;
import com.exchange.scanner.security.dto.request.RequestTokenRefresh;
import com.exchange.scanner.security.dto.request.SignInRequest;
import com.exchange.scanner.security.dto.request.SignUpRequest;
import com.exchange.scanner.security.dto.response.LoginResponse;
import com.exchange.scanner.security.dto.response.RefreshTokenResponse;
import com.exchange.scanner.dto.response.SimpleResponse;
import com.exchange.scanner.security.repository.UserRepository;
import com.exchange.scanner.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class SecurityController {

    private final UserRepository userRepository;
    private final SecurityService securityService;


    @PostMapping("/sign-in")
    public ResponseEntity<LoginResponse> signIn(@RequestBody SignInRequest signInRequest) {
        return ResponseEntity.ok(securityService.authenticateUser(signInRequest));
    }

    @PostMapping("/sign-up")
    public ResponseEntity<RegisterResponse> signUp(@RequestBody SignUpRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.username())) {
            throw new UserAlreadyExistException("Пользователь с данным e-mail уже зарегистрирован");
        }
        RegisterResponse response = securityService.register(signUpRequest);
        return ResponseEntity.created(URI.create("/users/" + response.userId())).body(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<RefreshTokenResponse> refreshToken(@RequestBody RequestTokenRefresh tokenRefresh) {
        return ResponseEntity.ok(securityService.refreshToken(tokenRefresh));
    }

    @PostMapping("/logout")
    public ResponseEntity<SimpleResponse> logout(@AuthenticationPrincipal UserDetails userDetails) {
        securityService.logout();
        return ResponseEntity.ok(new SimpleResponse("Вы вышли из аккаунта"));
    }
}
