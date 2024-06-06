package com.exchange.scanner.services.impl;

import com.exchange.scanner.dto.response.SimpleResponse;
import com.exchange.scanner.dto.response.exchangedata.ExchangeDataResponse;
import com.exchange.scanner.dto.response.exchangedata.coinsdata.CoinDataTicker;
import com.exchange.scanner.error.NoExchangesException;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

        Map<String, Set<Coin>> updatedCoinsMap = getCoinsAsync();

        updateCoins(updatedCoinsMap);

        long end = System.currentTimeMillis() - start;
        System.out.println("время выполнения обновления валют: " + (end / 1000) + "s");

        return new SimpleResponse("Обновление списка валют успешно завершено");
    }

    private Map<String, Set<Coin>> getCoinsAsync() {
        List<Exchange> exchanges = exchangeRepository.findAll();
        if (exchanges.isEmpty()) throw new NoExchangesException("Отсутствует список бирж для обновления монет");
        Map<String, Set<Coin>> exchangeMap = new ConcurrentHashMap<>();
        ExecutorService executorService = Executors.newFixedThreadPool(exchanges.size());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
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

    private void updateCoins(Map<String, Set<Coin>> coinsMap) {
        Set<Exchange> exchangesToUpdate = new HashSet<>();
        coinsMap.forEach((exchangeName, coins) -> {
            Exchange exchange = exchangeRepository.findByName(exchangeName);
            exchange.getCoins().clear();
            exchange.setCoins(coins);
            exchangesToUpdate.add(exchange);
        });

        saveExchangesAndCoins(exchangesToUpdate);
    }

    @Transactional
    private void saveExchangesAndCoins(Set<Exchange> exchangesToUpdate) {
        long start = System.currentTimeMillis();
        coinRepository.deleteAllInBatch();
        exchangeRepository.saveAll(exchangesToUpdate);
        long end = System.currentTimeMillis() - start;
        System.out.println("Время выполнения метода saveExchangesAndCoins равно: " + end / 1000 + "s");
    }

    @Override
    @Scheduled(fixedRate = 10000)
    public void checkArbitrageOpportunities() {

        List<Exchange> exchanges = exchangeRepository.findAll();
        Map<String, List<CoinDataTicker>> coinsMap = getCoinsPricesAsync(exchanges);

        coinsMap.forEach((exchangeForBuy, coinsForBuy) ->
            coinsMap.forEach((exchangeForSell, coinsForSell) -> {
                if (!exchangeForBuy.equals(exchangeForSell)) {
                    coinsForBuy.parallelStream().forEach(coinBuy ->
                        coinsForSell.parallelStream()
                            .filter(coinSell -> coinSell.getSymbol().equals(coinBuy.getSymbol()))
                            .forEach(coinSell -> {
                                BigDecimal buyPrice = new BigDecimal(coinBuy.getBid());
                                BigDecimal sellPrice = new BigDecimal(coinSell.getAsk());
                                BigDecimal spread = sellPrice.subtract(buyPrice);
                                if (spread.compareTo(BigDecimal.ZERO) > 0) {
                                    System.out.println("spread");
                                }
                            })
                    );
                }
            })
        );
    }

    private Map<String, List<CoinDataTicker>> getCoinsPricesAsync(List<Exchange> exchanges) {
        Map<String, List<CoinDataTicker>> coinsMap = new ConcurrentHashMap<>();
        ExecutorService executorService = Executors.newFixedThreadPool(exchanges.size());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        exchanges.forEach(buyExchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<Coin> coins = buyExchange.getCoins();
                Map<String, List<CoinDataTicker>> coinPriceMap = apiExchangeAdapter
                        .getCoinPrice(buyExchange.getName(), coins);
                synchronized (coinsMap) {
                    coinsMap.putAll(coinPriceMap);
                }
            }, executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return coinsMap;
    }
}
