package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CoinChainUtils {

    private static final Logger log = LoggerFactory.getLogger(CoinChainUtils.class);

    public Set<ChainResponseDTO> getCoinsChainInfoAsync(ApiExchangeAdapter apiExchangeAdapter,
                                                         ExchangeRepository exchangeRepository,
                                                         UserMarketSettingsRepository userMarketSettingsRepository
    ) {
        Set<ChainResponseDTO> result = Collections.synchronizedSet(new HashSet<>());
        Set<Exchange> userExchanges = AppServiceUtils.getUsersExchanges(userMarketSettingsRepository, exchangeRepository);
        if (userExchanges == null || userExchanges.isEmpty()) return result;
        Set<String> usersCoinsNames = AppServiceUtils.getUsersCoinsNames(userMarketSettingsRepository);

        ExecutorService executorService = Executors.newFixedThreadPool(userExchanges.size());
        List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();

        userExchanges.forEach(exchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<Coin> filteredCoins = AppServiceUtils.getFilteredCoins(exchange, usersCoinsNames);
                Set<ChainResponseDTO> chainResponseDTOSet = apiExchangeAdapter.getCoinChain(exchange.getName(), filteredCoins);
                synchronized (result) {
                    result.addAll(chainResponseDTOSet);
                }
            }, executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();

        return result;
    }
}
