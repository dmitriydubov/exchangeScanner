package com.exchange.scanner.security.service;

import com.exchange.scanner.security.model.RefreshToken;

import java.util.Optional;

public interface RefreshTokenService {
    Optional<RefreshToken> findByRefreshToken(String token);
    RefreshToken createRefreshToken(Long userId);
    RefreshToken checkRefreshToken(RefreshToken token);
    void deleteByUserId(Long id);
}
