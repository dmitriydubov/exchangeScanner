package com.exchange.scanner.services;

import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;

import java.util.Set;

public interface ApiExchangeAdapter {
    Set<Coin> refreshExchangeCoins(Exchange exchange);
}
