package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RefreshVolume24hUtils {
    public Map<String, Set<Coin>> getVolume24hAsync(ApiExchangeAdapter apiExchangeAdapter,
                                                    ExchangeRepository exchangeRepository,
                                                    UserMarketSettingsRepository userMarketSettingsRepository)
    {
        Map<String, Set<Coin>> result = new HashMap<>();
        Set<Exchange> userExchanges = AppServiceUtils.getUsersExchanges(userMarketSettingsRepository, exchangeRepository);
        Set<String> usersCoinsNames = AppServiceUtils.getUsersCoinsNames(userMarketSettingsRepository);
        ExecutorService executorService = Executors.newFixedThreadPool(userExchanges.size());
        List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();

        userExchanges.forEach(exchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<Coin> filteredCoinsNames = AppServiceUtils.getFilteredCoins(exchange, usersCoinsNames);
                synchronized (result) {
                    result.putAll(apiExchangeAdapter.getCoinVolume24h(exchange.getName(), filteredCoinsNames));
                }
            }, executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();

        return result;
    }
}
