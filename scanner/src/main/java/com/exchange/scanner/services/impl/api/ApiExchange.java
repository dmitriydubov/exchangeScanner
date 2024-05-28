package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.model.Coin;

import java.util.Set;

public interface ApiExchange {
    Set<Coin> getAllCoins();
}
