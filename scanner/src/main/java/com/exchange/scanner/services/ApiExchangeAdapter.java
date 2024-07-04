package com.exchange.scanner.services;

import com.exchange.scanner.dto.response.exchangedata.responsedata.CoinDataTicker;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ApiExchangeAdapter {
    Set<Coin> refreshExchangeCoins(Exchange exchange);

    Map<String, Set<Coin>> getCoinVolume24h(String exchangeName, Set<Coin> coins);

    Set<CoinDepth> getOrderBook(Exchange exchange, Set<String> coins);

    Map<String, Set<Coin>> getCoinChain(String exchange, Set<Coin> coinsSet);

    Map<String, Set<Coin>> getTradingFee(String exchange, Set<Coin> coinsSet);
}
