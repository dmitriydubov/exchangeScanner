package com.exchange.scanner.services;

import com.exchange.scanner.dto.response.event.ArbitrageEvent;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface AppService {

    CompletableFuture<Set<String>> getExchanges();

    CompletableFuture<Set<ArbitrageEvent>> getArbitrageOpportunities(UserDetails userDetails);

    void refreshCoins();

    void getOrderBooks();

    void getCoinsChains();

    void getTradingFee();

    void getVolume24h();

    void getCoinMarketCapCoinInfo();
}
