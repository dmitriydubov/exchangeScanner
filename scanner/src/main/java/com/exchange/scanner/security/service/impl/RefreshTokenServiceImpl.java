package com.exchange.scanner.security.service.impl;

import com.exchange.scanner.security.error.RefreshTokenException;
import com.exchange.scanner.security.model.RefreshToken;
import com.exchange.scanner.security.repository.RefreshTokenRepository;
import com.exchange.scanner.security.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    @Value("${jwt.refreshTokenLifetime}")
    private Duration refreshTokenExpiration;

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    public Optional<RefreshToken> findByRefreshToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Override
    public RefreshToken createRefreshToken(Long userId) {
        var refreshToken = RefreshToken.builder()
                .userId(userId)
                .expiredDate(Instant.now().plusMillis(refreshTokenExpiration.toMillis()))
                .token(UUID.randomUUID().toString())
                .build();
        refreshToken = refreshTokenRepository.save(refreshToken);

        return refreshToken;
    }

    @Override
    public RefreshToken checkRefreshToken(RefreshToken token) {
        if (token.getExpiredDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw  new RefreshTokenException(token.getToken(), "Refresh token просрочен. Пройдите авторизацию заново");
        }

        return token;
    }

    @Override
    public void deleteByUserId(Long id) {
        refreshTokenRepository.deleteByUserId(id);
    }


}
