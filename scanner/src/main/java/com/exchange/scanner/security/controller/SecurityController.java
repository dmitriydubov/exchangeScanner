package com.exchange.scanner.security.controller;

import com.exchange.scanner.security.dto.request.*;
import com.exchange.scanner.security.dto.response.PasswordResetResponse;
import com.exchange.scanner.security.dto.response.RegisterResponse;
import com.exchange.scanner.security.error.EmptyFieldException;
import com.exchange.scanner.security.error.IllegalConfirmationCodeException;
import com.exchange.scanner.security.error.PasswordConfirmationException;
import com.exchange.scanner.security.error.UserAlreadyExistException;
import com.exchange.scanner.security.dto.response.LoginResponse;
import com.exchange.scanner.security.dto.response.RefreshTokenResponse;
import com.exchange.scanner.dto.response.SimpleResponse;
import com.exchange.scanner.security.repository.ConfirmationCoinRepository;
import com.exchange.scanner.security.repository.UserRepository;
import com.exchange.scanner.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class SecurityController {

    private final UserRepository userRepository;
    private final SecurityService securityService;
    private final ConfirmationCoinRepository confirmationCoinRepository;


    @PostMapping("/sign-in")
    public ResponseEntity<LoginResponse> signIn(@RequestBody SignInRequest signInRequest) {
        return ResponseEntity.ok(securityService.authenticateUser(signInRequest));
    }

    @PostMapping("/sign-up")
    public ResponseEntity<RegisterResponse> signUp(@RequestBody SignUpRequest signUpRequest) {
        if (
                signUpRequest.username() == null || signUpRequest.username().isEmpty() ||
                signUpRequest.email() == null || signUpRequest.email().isEmpty() ||
                signUpRequest.telegram() == null || signUpRequest.telegram().isEmpty() ||
                signUpRequest.password() == null || signUpRequest.password().isEmpty() ||
                signUpRequest.confirmPassword() == null || signUpRequest.confirmPassword().isEmpty()
        ) {
            throw new EmptyFieldException("Все поля регистрации должны быть заполнены");
        }
        if (
                userRepository.existsByUsername(signUpRequest.username()) ||
                userRepository.existsByEmail(signUpRequest.email()) ||
                userRepository.existsByTelegram(signUpRequest.telegram())
        ) {
            throw new UserAlreadyExistException("Пользователь с данными учетными данными уже зарегистрирован");
        }
        if (!signUpRequest.password().equals(signUpRequest.confirmPassword())) {
            throw new PasswordConfirmationException("Пароли не совпадают");
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

    @PostMapping("/reset-password")
    public ResponseEntity<PasswordResetResponse> resetPassword(@RequestBody PasswordResetRequest passwordResetRequest) {
        if (
                passwordResetRequest.username() == null || passwordResetRequest.username().isEmpty() ||
                passwordResetRequest.email() == null || passwordResetRequest.email().isEmpty() ||
                passwordResetRequest.password() == null || passwordResetRequest.password().isEmpty() ||
                passwordResetRequest.confirmPassword() == null || passwordResetRequest.confirmPassword().isEmpty()
        ) {
            throw new EmptyFieldException("Все поля регистрации должны быть заполнены");
        }
        if (!passwordResetRequest.password().equals(passwordResetRequest.confirmPassword())) {
            throw new PasswordConfirmationException("Пароли не совпадают");
        }
        return ResponseEntity.ok(securityService.getPasswordResetConfirmationCode(passwordResetRequest));
    }

    @PostMapping("/confirm-password")
    public ResponseEntity<SimpleResponse> confirmReset(@RequestBody PasswordResetConfirmRequest passwordResetConfirmRequest) {
        return ResponseEntity.ok(securityService.confirmResetCode(passwordResetConfirmRequest));
    }
}
