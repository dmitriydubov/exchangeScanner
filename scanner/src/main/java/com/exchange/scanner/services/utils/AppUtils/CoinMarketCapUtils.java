package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.dto.response.CoinInfoDTO;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;
import com.exchange.scanner.services.CoinMarketCapService;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CoinMarketCapUtils {

    public Set<CoinInfoDTO> getCoinMarketCapCoinInfo(
             ExchangeRepository exchangeRepository,
             CoinMarketCapService coinMarketCapService,
             UserMarketSettingsRepository userMarketSettingsRepository
    ) {
        Set<CoinInfoDTO> result = Collections.synchronizedSet(new HashSet<>());
        Set<Exchange> exchanges = AppServiceUtils.getUsersExchanges(userMarketSettingsRepository, exchangeRepository);
        if (exchanges.isEmpty()) return new HashSet<>();
        Set<String> usersCoinsNames = AppServiceUtils.getUsersCoinsNames(userMarketSettingsRepository);

        exchanges.forEach(exchange -> {
            Set<Coin> filteredCoins = AppServiceUtils.getFilteredCoins(exchange, usersCoinsNames);
            Set<CoinInfoDTO> response = coinMarketCapService.getCoinMarketCapCoinsInfo(filteredCoins, exchange.getName());
            result.addAll(response);
        });

        return result;
    }
}
