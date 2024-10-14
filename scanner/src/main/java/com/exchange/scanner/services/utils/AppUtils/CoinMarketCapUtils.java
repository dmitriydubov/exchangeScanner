package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.dto.response.CoinInfoDTO;
import com.exchange.scanner.model.ArbitrageEvent;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.repositories.ArbitrageEventRepository;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;
import com.exchange.scanner.services.CoinMarketCapService;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class CoinMarketCapUtils {

    public Set<CoinInfoDTO> getCoinMarketCapCoinInfo(CoinMarketCapService coinMarketCapService,
                                                     Set<String> arbitrageEvents)
    {

        return coinMarketCapService.getCoinMarketCapCoinsInfo(arbitrageEvents);
    }
}
