package com.exchange.scanner.services.impl;

import com.exchange.scanner.dto.response.SimpleResponse;
import com.exchange.scanner.dto.response.exchangedata.ExchangeDataResponse;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.UserMarketSettings;
import com.exchange.scanner.repositories.CoinRepository;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;
import com.exchange.scanner.security.model.User;
import com.exchange.scanner.security.repository.UserRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;
import com.exchange.scanner.services.AppService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class AppServiceImpl implements AppService {

    private final ExchangeRepository exchangeRepository;
    private final UserMarketSettingsRepository userMarketSettingsRepository;
    private final UserRepository userRepository;
    private final ApiExchangeAdapter apiExchangeAdapter;
    private final CoinRepository coinRepository;

    @Override
    public Set<Exchange> getExchanges() {
        return new HashSet<>(exchangeRepository.findAll());
    }

    @Override
    public ExchangeDataResponse getExchangeData(UserDetails userDetails) {
        var user = userRepository.findByUsername("schliffen@mail.ru").orElseThrow(() -> new UsernameNotFoundException("Пользователь не зарегистрирован"));
        UserMarketSettings userMarketSettings;
        Optional<UserMarketSettings> optional = userMarketSettingsRepository.getByUserId(user);
        userMarketSettings = optional.orElseGet(() -> createUserMarketSettingsWithDefaults(user));

        return null;
    }

    private UserMarketSettings createUserMarketSettingsWithDefaults(User user) {
        List<String> exchangesNames = exchangeRepository.findAll().stream().map(Exchange::getName).toList();
        var userMarketSettings = UserMarketSettings.builder()
                .userId(user)
                .coins(new ArrayList<>())
                .marketsBuy(exchangesNames)
                .marketsSell(exchangesNames)
                .minVolume(10.0)
                .maxVolume(1_000_000.0)
                .profitSpread(0.0)
                .percentSpread(0.0)
                .build();

        return userMarketSettingsRepository.save(userMarketSettings);
    }

    @Override
    public SimpleResponse refreshCoins() {
        long start = System.currentTimeMillis();

        Set<Coin> updatedCoins = getCoinsAsync();

        saveCoins(updatedCoins);

        long end = System.currentTimeMillis() - start;
        System.out.println("время выполнения обновления валют: " + (end / 1000) + "s");

        return new SimpleResponse("Обновление списка валют успешно завершено");
    }

    private Set<Coin> getCoinsAsync() {
        List<Exchange> exchanges = exchangeRepository.findAll();
        Set<Coin> updatedCoins = Collections.synchronizedSet(new HashSet<>());
        ExecutorService executorService = Executors.newFixedThreadPool(exchanges.size());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        exchanges.forEach(exchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<Coin> coins = apiExchangeAdapter.refreshExchangeCoins(exchange);
                synchronized (updatedCoins) {
                    updatedCoins.addAll(coins);
                }
            }, executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();
        return updatedCoins;
    }

    @Transactional
    private void saveCoins(Set<Coin> coins) {
        Set<Coin> dataCoins = new HashSet<>(coinRepository.findAll());
        Set<Coin> newCoins = new HashSet<>(coins);
        newCoins.removeAll(dataCoins);
        coinRepository.saveAll(newCoins);

        Set<Coin> coinsToDelete = new HashSet<>(dataCoins);
        coinsToDelete.removeAll(coins);
        coinRepository.deleteAll(coinsToDelete);
    }
}
