package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.dto.response.CoinInfoDTO;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;
import com.exchange.scanner.services.CoinMarketCapService;

import java.util.HashSet;
import java.util.Set;

public class CoinMarketCapUtils {

    public Set<CoinInfoDTO> getCoinMarketCapCoinInfo(
             ExchangeRepository exchangeRepository,
             CoinMarketCapService coinMarketCapService,
             UserMarketSettingsRepository userMarketSettingsRepository
    ) {
        Set<Exchange> exchanges = AppServiceUtils.getUsersExchanges(userMarketSettingsRepository, exchangeRepository);
        if (exchanges.isEmpty()) return new HashSet<>();
        Set<String> usersCoinsNames = AppServiceUtils.getUsersCoinsNames(userMarketSettingsRepository);

        return coinMarketCapService.getCoinMarketCapCoinsInfo(usersCoinsNames);
    }
}
