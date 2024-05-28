package com.exchange.scanner.repositories;

import com.exchange.scanner.model.UserMarketSettings;
import com.exchange.scanner.security.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserMarketSettingsRepository extends JpaRepository<UserMarketSettings, Long> {
    Optional<UserMarketSettings> getByUserId(User user);
}
