package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class RefreshCoinUtils {

    public Map<String, Set<Coin>> getCoinsAsync(ExchangeRepository exchangeRepository,
                                                ApiExchangeAdapter apiExchangeAdapter
    ) {
        List<Exchange> exchanges = exchangeRepository.findAll();
        if (exchanges.isEmpty()) return new HashMap<>();
        return getExchangeMap(exchanges, apiExchangeAdapter);
    }

    private Map<String, Set<Coin>> getExchangeMap(List<Exchange> exchanges, ApiExchangeAdapter apiExchangeAdapter) {
        Map<String, Set<Coin>> exchangeMap = new ConcurrentHashMap<>();
        ExecutorService executorService = Executors.newFixedThreadPool(exchanges.size());
        List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();
        exchanges.forEach(exchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<Coin> coins = apiExchangeAdapter.refreshExchangeCoins(exchange);
                synchronized (exchangeMap) {
                    exchangeMap.put(exchange.getName(), coins);
                }
            }, executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();
        return exchangeMap;
    }
}
