package com.exchange.scanner.security.service.impl;

import com.exchange.scanner.dto.response.SimpleResponse;
import com.exchange.scanner.security.dto.request.*;
import com.exchange.scanner.security.dto.response.PasswordResetResponse;
import com.exchange.scanner.security.dto.response.RegisterResponse;
import com.exchange.scanner.security.error.IllegalConfirmationCodeException;
import com.exchange.scanner.security.error.NoSuchUserException;
import com.exchange.scanner.security.error.RefreshTokenException;
import com.exchange.scanner.security.dto.response.LoginResponse;
import com.exchange.scanner.security.dto.response.RefreshTokenResponse;
import com.exchange.scanner.security.model.RefreshToken;
import com.exchange.scanner.security.model.User;
import com.exchange.scanner.security.repository.ConfirmationCoinRepository;
import com.exchange.scanner.security.repository.UserRepository;
import com.exchange.scanner.security.service.RefreshTokenService;
import com.exchange.scanner.security.service.SecurityService;
import com.exchange.scanner.security.service.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SecurityServiceImpl implements SecurityService {
    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;

    private final JwtUtils jwtUtils;

    private final RefreshTokenService refreshTokenService;

    private final MailService mailService;

    private final ConfirmationCoinRepository confirmationCoinRepository;

    public LoginResponse authenticateUser(SignInRequest signInRequest) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                signInRequest.username(),
                signInRequest.password()
        ));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());

        return LoginResponse.builder()
                .id(userDetails.getId())
                .token(jwtUtils.generateToken(userDetails))
                .refreshToken(refreshToken.getToken())
                .username(userDetails.getUsername())
                .roles(roles).build();
    }

    public RegisterResponse register(SignUpRequest signUpRequest) {
        var user = User.builder()
                .username(signUpRequest.username())
                .email(signUpRequest.email())
                .telegram(signUpRequest.telegram())
                .password(passwordEncoder.encode(signUpRequest.password()))
                .regTime(new Date())
                .roles(signUpRequest.roles())
                .build();
        User registeredUser = userRepository.save(user);

        return new RegisterResponse(registeredUser.getId(), registeredUser.getUsername(), jwtUtils.generateToken(registeredUser.getUsername()));
    }

    public RefreshTokenResponse refreshToken(RequestTokenRefresh request) {
        String refreshToken = request.refreshToken();
        return refreshTokenService.findByRefreshToken(refreshToken)
                .map(refreshTokenService::checkRefreshToken)
                .map(RefreshToken::getUserId)
                .map(userId -> {
                    User tokenOwner = userRepository.findById(userId)
                            .orElseThrow(() -> new RefreshTokenException("Ошибка получения токена для userId: " + userId));
                    String token = jwtUtils.generateTokenFromUsername(tokenOwner.getUsername());
                    return new RefreshTokenResponse(token, refreshTokenService.createRefreshToken(userId).getToken());
                }).orElseThrow(() -> new RefreshTokenException(refreshToken, "Refresh token не найден"));
    }

    public void logout() {
        var currentPrincipal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (currentPrincipal instanceof AppUserDetails userDetails) {
            Long userId = userDetails.getId();
            refreshTokenService.deleteByUserId(userId);
        }
    }

    @Override
    public PasswordResetResponse getPasswordResetConfirmationCode(PasswordResetRequest passwordResetRequest) {
        User user = userRepository.findByUsername(passwordResetRequest.username())
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не зарегистрирован"));

        new Thread(() -> {
            mailService.sendEmail("Код подтверждения", passwordResetRequest.email());
        }).start();

        return new PasswordResetResponse(passwordResetRequest);
    }

    @Override
    public SimpleResponse confirmResetCode(PasswordResetConfirmRequest passwordResetConfirmRequest) {
        var codeEntity = confirmationCoinRepository
                .findByEmail(passwordResetConfirmRequest.passwordResetRequest().email())
                .orElseThrow(() -> new RuntimeException("Код отсутствует"));

        if (!codeEntity.getCode().equals(passwordResetConfirmRequest.code())) {
            confirmationCoinRepository.deleteById(codeEntity.getId());
            throw new IllegalConfirmationCodeException("Неверный код");
        }

        var user = userRepository.findByUsername(passwordResetConfirmRequest.passwordResetRequest().username())
                .orElseThrow(() -> new NoSuchUserException("Пользователь не зарегистрирован"));

        user.setPassword(passwordEncoder.encode(passwordResetConfirmRequest.passwordResetRequest().password()));
        userRepository.save(user);
        confirmationCoinRepository.deleteById(codeEntity.getId());

        return new SimpleResponse("Пароль успешно обновлен");
    }
}
