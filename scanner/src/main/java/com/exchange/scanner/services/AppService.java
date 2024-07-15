package com.exchange.scanner.services;

import com.exchange.scanner.dto.request.UserUpdateMarketData;
import com.exchange.scanner.dto.response.ExchangeData;
import com.exchange.scanner.dto.response.SimpleResponse;
import com.exchange.scanner.dto.response.event.ArbitrageEvent;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface AppService {

    CompletableFuture<ExchangeData> getExchanges(UserDetails userDetails);

    CompletableFuture<Set<ArbitrageEvent>> getArbitrageEvents(UserDetails userDetails);

    void refreshCoins();

    void getOrderBooks();

    void getCoinsChains();

    void getTradingFee();

    void getVolume24h();

    void getCoinMarketCapCoinInfo();

    CompletableFuture <ExchangeData> updateUserMarketData(UserUpdateMarketData userData, UserDetails userDetails);
}
