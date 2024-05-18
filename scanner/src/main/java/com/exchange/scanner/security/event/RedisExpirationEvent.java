package com.exchange.scanner.security.event;

import com.exchange.scanner.security.model.RefreshToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisKeyExpiredEvent;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RedisExpirationEvent {

    @EventListener
    public void handleRedisKeyExpiredEvent(RedisKeyExpiredEvent<RefreshToken> event) {
        RefreshToken expiredrefreshToken = (RefreshToken) event.getValue();

        if (expiredrefreshToken == null) {
            throw  new RuntimeException("Refresh token - null в функции RedisKeyExpiredEvent");
        }

        log.info("Refresh token с ключом={} просрочен! RefreshToken - {}", expiredrefreshToken.getId(), expiredrefreshToken.getToken());
    }
}
