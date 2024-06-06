package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.coinsdata.CoinDataTicker;
import com.exchange.scanner.model.Coin;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ApiExchange {
    Set<Coin> getAllCoins();

    Map<String, List<CoinDataTicker>> getCoinDataTicker(Set<Coin> coins);
}
