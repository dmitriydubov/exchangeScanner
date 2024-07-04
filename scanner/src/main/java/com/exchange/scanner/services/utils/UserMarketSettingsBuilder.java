package com.exchange.scanner.services.utils;

import com.exchange.scanner.model.UserMarketSettings;
import com.exchange.scanner.security.model.User;

import java.util.List;

public class UserMarketSettingsBuilder {

    private static final double DEFAULT_MAX_VOLUME = 1_000_000.0;
    private static final double DEFAULT_MIN_VOLUME = 10.0;
    private static final double DEFAULT_PROFIT_SPREAD = 1.0;
    private static final double DEFAULT_PERCENT_SPREAD = 0.0;

    public static UserMarketSettings getDefaultUserMarketSettings(User user, List<String> exchangesNames) {
        return UserMarketSettings.builder()
                .user(user)
                .coins(List.of("WLTH", "MICHI", "BLOCK", "BENDOG", "ENJ", "DOGA", "PIT", "KZEN", "CRETA", "NEOX", "BTC", "ETC"))
                .marketsBuy(exchangesNames)
                .marketsSell(exchangesNames)
                .minVolume(DEFAULT_MIN_VOLUME)
                .maxVolume(DEFAULT_MAX_VOLUME)
                .profitSpread(DEFAULT_PROFIT_SPREAD)
                .percentSpread(DEFAULT_PERCENT_SPREAD)
                .build();
    }
}
