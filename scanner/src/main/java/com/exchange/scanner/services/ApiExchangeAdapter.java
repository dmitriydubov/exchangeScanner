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

    Map<String, List<CoinDataTicker>> getCoinPrice(String exchangeName, Set<Coin> coins);

    Set<CoinDepth> getOrderBook(Exchange exchange, Set<String> coins);
}
