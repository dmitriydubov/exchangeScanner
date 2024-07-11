package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.model.*;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;

import java.util.*;
import java.util.stream.Collectors;

public class AppServiceUtils {


    public static Set<Exchange> getUsersExchanges(UserMarketSettingsRepository userMarketSettingsRepository,
                                                  ExchangeRepository exchangeRepository
    ) {
        return userMarketSettingsRepository.findAll().stream()
                .flatMap(settings -> {
                    List<String> marketsBuy = settings.getMarketsBuy();
                    List<String> marketsSell = settings.getMarketsSell();
                    List<String> allMarketsNames = new ArrayList<>();
                    allMarketsNames.addAll(marketsBuy);
                    allMarketsNames.addAll(marketsSell);
                    return allMarketsNames.stream();
                })
                .map(exchangeRepository::findByName)
                .collect(Collectors.toSet());
    }

    public static Set<String> getUsersCoinsNames(UserMarketSettingsRepository userMarketSettingsRepository) {
        return userMarketSettingsRepository.findAll().stream()
                .flatMap(settings -> settings.getCoins().stream())
                .collect(Collectors.toSet());
    }

    public static synchronized Set<Coin> getFilteredCoins(Exchange exchange, Set<String> usersCoinsNames) {
        return exchange.getCoins().stream()
                .filter(coin -> usersCoinsNames.contains(coin.getName()))
                .collect(Collectors.toSet());
    }
}
