package com.exchange.scanner.services.utils;

import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.error.NoExchangesException;
import com.exchange.scanner.model.*;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class AppServiceUtils {

    public static Map<String, Set<Coin>> getCoinsAsync(ExchangeRepository exchangeRepository,
                                                       ApiExchangeAdapter apiExchangeAdapter
    ) {
        List<Exchange> exchanges = exchangeRepository.findAll();
        if (exchanges.isEmpty()) throw new NoExchangesException("Отсутствует список бирж для обновления монет");
        return AppServiceUtils.getExchangeMap(exchanges, apiExchangeAdapter);
    }

    private static Map<String, Set<Coin>> getExchangeMap(List<Exchange> exchanges, ApiExchangeAdapter apiExchangeAdapter) {
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

    public static Set<Exchange> getUsersExchanges(UserMarketSettingsRepository userMarketSettingsRepository,
                                                  ExchangeRepository exchangeRepository
    ) {
        return userMarketSettingsRepository.findAll().stream()
                .flatMap(settings -> {
                    List<String> marketsBuy = settings.getMarketsBuy();
                    List<String> marketsSell = settings.getMarketsSell();
                    List<String> allMarketsNames = new ArrayList<>();
                    allMarketsNames.addAll(marketsBuy);
                    allMarketsNames.addAll(marketsSell);
                    return allMarketsNames.stream();
                })
                .map(exchangeRepository::findByName)
                .collect(Collectors.toSet());
    }

    public static Set<String> getUsersCoinsNames(UserMarketSettingsRepository userMarketSettingsRepository) {
        return userMarketSettingsRepository.findAll().stream()
                .flatMap(settings -> settings.getCoins().stream())
                .collect(Collectors.toSet());
    }

    public synchronized static Set<String> getFilteredCoinsNames(Exchange exchange, Set<String> usersCoinsNames) {
        List<String> exchangesCoinsNames = new ArrayList<>(exchange.getCoins().stream().map(Coin::getSymbol).toList());
        exchangesCoinsNames.addAll(usersCoinsNames);
        Map<String, Long> map = exchangesCoinsNames.stream()
                .collect(Collectors.groupingBy(n -> n, Collectors.counting()));

        return map.entrySet()
                .stream()
                .filter(el -> el.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public static OrdersBook createOrderBook(Exchange exchange, CoinDepth depth) {

        OrdersBook ordersBook = new OrdersBook();
        ordersBook.setExchange(exchange);
        ordersBook.setCoin(
                exchange.getCoins().stream()
                        .filter(coin -> coin.getName().equals(depth.getCoinName()))
                        .findFirst().orElseThrow(() -> new RuntimeException("Ошибка в методе getOrderBooks. Монеты нет в списке монет биржи"))
        );

        List<Bid> bids = depth.getCoinDepthBids().stream()
                .map(depthBid -> {
                    Bid bid = new Bid();
                    bid.setOrdersBook(ordersBook);
                    bid.setPrice(new BigDecimal(depthBid.getPrice()));
                    bid.setVolume(new BigDecimal(depthBid.getVolume()));
                    return bid;
                })
                .toList();

        List<Ask> asks = depth.getCoinDepthAsks().stream()
                .map(depthAsk -> {
                    Ask ask = new Ask();
                    ask.setOrdersBook(ordersBook);
                    ask.setPrice(new BigDecimal(depthAsk.getPrice()));
                    ask.setVolume(new BigDecimal(depthAsk.getVolume()));
                    return ask;
                })
                .toList();

        ordersBook.setBids(bids);
        ordersBook.setAsks(asks);

        return ordersBook;
    }

    public static List<OrdersBook> getOrderBooksAsync(
            ExchangeRepository exchangeRepository,
            ApiExchangeAdapter apiExchangeAdapter,
            UserMarketSettingsRepository userMarketSettingsRepository
    ) {
        List<OrdersBook> ordersBooks = Collections.synchronizedList(new ArrayList<>());
        Set<Exchange> exchanges = AppServiceUtils.getUsersExchanges(userMarketSettingsRepository, exchangeRepository);
        if (exchanges.isEmpty()) throw new RuntimeException("Нет аккаунтов пользователей");
        Set<String> usersCoinsNames = AppServiceUtils.getUsersCoinsNames(userMarketSettingsRepository);
        ExecutorService executorService = Executors.newFixedThreadPool(exchanges.size());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        exchanges.forEach(exchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<String> filteredCoinsNames = AppServiceUtils.getFilteredCoinsNames(exchange, usersCoinsNames);
                Set<CoinDepth> coinDepth = apiExchangeAdapter.getOrderBook(exchange, filteredCoinsNames);
                coinDepth.forEach(depth -> {
                    OrdersBook ordersBook = AppServiceUtils.createOrderBook(exchange, depth);
                    ordersBooks.add(ordersBook);
                });
            },executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return ordersBooks;
    }

    public static Map<String, Set<Ask>> getBuyPrices(List<OrdersBook> ordersBooks) {
        return ordersBooks.stream()
                .collect(Collectors.toMap(
                        ordersBook -> ordersBook.getExchange().getName(),
                        ordersBook -> new TreeSet<>(ordersBook.getAsks())
                ));
    }

    public static Map<String, Set<Bid>> getSellPrices(List<OrdersBook> ordersBooks) {
        return ordersBooks.stream()
                .collect(Collectors.toMap(
                        ordersBook -> ordersBook.getExchange().getName(),
                        ordersBook -> new TreeSet<>(ordersBook.getBids()).reversed()
                ));
    }
}
