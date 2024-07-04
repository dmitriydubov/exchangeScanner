package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.exchangedata.responsedata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ApiExchange {
    Set<Coin> getAllCoins();

    Set<Coin> getCoinVolume24h(Set<Coin> coins);

    Set<CoinDepth> getOrderBook(Set<String> coins);

    Set<Coin> getCoinChain(Set<Coin> coins);

    Set<Coin> getTradingFee(Set<Coin> coins);
}
