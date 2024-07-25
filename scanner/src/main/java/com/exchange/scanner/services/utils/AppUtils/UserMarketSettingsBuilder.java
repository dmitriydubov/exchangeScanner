package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.model.UserMarketSettings;
import com.exchange.scanner.security.model.User;

import java.math.BigDecimal;
import java.util.List;

public class UserMarketSettingsBuilder {

    private static final BigDecimal DEFAULT_MAX_VOLUME = BigDecimal.valueOf(10_000);
    private static final BigDecimal DEFAULT_MIN_VOLUME = BigDecimal.valueOf(10.0);
    private static final BigDecimal DEFAULT_PROFIT_SPREAD = BigDecimal.valueOf(1.0);

    public static UserMarketSettings getDefaultUserMarketSettings(User user, List<String> exchangesNames) {
        return UserMarketSettings.builder()
                .user(user)
                .coins(List.of("C98"))
                .marketsBuy(exchangesNames)
                .marketsSell(exchangesNames)
                .minVolume(DEFAULT_MIN_VOLUME)
                .maxVolume(DEFAULT_MAX_VOLUME)
                .profitSpread(DEFAULT_PROFIT_SPREAD)
                .build();
    }
}
