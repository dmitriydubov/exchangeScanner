package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TradingFeeUtils {
    public Set<TradingFeeResponseDTO> getTradingFeeAsync(ApiExchangeAdapter apiExchangeAdapter,
                                                    ExchangeRepository exchangeRepository,
                                                    UserMarketSettingsRepository userMarketSettingsRepository
    ) {
        Set<TradingFeeResponseDTO> result = Collections.synchronizedSet(new HashSet<>());
        Set<Exchange> exchanges = new HashSet<>(exchangeRepository.findAll());
        ExecutorService executorService = Executors.newFixedThreadPool(exchanges.size());
        List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();

        exchanges.forEach(exchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<Coin> coins = exchange.getCoins();
                Set<TradingFeeResponseDTO> response = apiExchangeAdapter.getTradingFee(exchange.getName(), coins);
                synchronized (result) {
                    result.addAll(response);
                }
            }, executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();

        return result;
    }
}
