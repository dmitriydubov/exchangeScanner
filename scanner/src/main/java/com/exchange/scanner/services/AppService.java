package com.exchange.scanner.services;

import com.exchange.scanner.dto.request.UserUpdateMarketData;
import com.exchange.scanner.dto.response.ExchangeData;
import com.exchange.scanner.dto.response.event.ArbitrageEventDTO;
import com.exchange.scanner.model.ArbitrageEvent;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface AppService {

    CompletableFuture<ExchangeData> getExchanges(UserDetails userDetails);

    CompletableFuture<Set<ArbitrageEventDTO>> getArbitrageEvents(UserDetails userDetails);

    void refreshCoins();

    void getOrderBooks();

    void getCoinsChains();

    void getTradingFee();

    void getVolume24h();

    void findArbitrageEvents();

    void getCoinMarketCapCoinInfo();

    CompletableFuture <ExchangeData> updateUserMarketData(UserUpdateMarketData userData, UserDetails userDetails);
}
