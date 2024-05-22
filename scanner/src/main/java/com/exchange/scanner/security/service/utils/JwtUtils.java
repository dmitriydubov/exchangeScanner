package com.exchange.scanner.security.service.utils;

import com.exchange.scanner.security.service.impl.AppUserDetails;
import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

@Component
@Slf4j
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.lifetime}")
    private Duration lifetime;

    public String generateToken(AppUserDetails userDetails) {
        return generateTokenFromUsername(userDetails.getUsername());
    }

    public String generateToken(String username) {
        return generateTokenFromUsername(username);
    }

    public String generateTokenFromUsername(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime() + lifetime.toMillis()))
                .signWith(SignatureAlgorithm.HS512, secret).compact();
    }

    public String getUsername(String token) {
        return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody().getSubject();
    }

    public Boolean validate(String authToken) {
        try {
            Jwts.parser().setSigningKey(secret).parseClaimsJws(authToken);
            return true;
        } catch (SignatureException ex) {
            log.error("Неверная подпись токена {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.error("Невалидный токен {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.error("Токен просрочен {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("Токен не поддерживается {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("пустые строки claims {}", ex.getMessage());
        }
        return false;
    }
}
