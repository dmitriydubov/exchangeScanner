package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Ask;
import com.exchange.scanner.model.Bid;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.OrdersBook;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class OrdersBookUtils {

    private static final int ORDERS_BOOK_SIZE_LIMIT = 10;

    public List<OrdersBook> getOrderBooksAsync(
            ExchangeRepository exchangeRepository,
            ApiExchangeAdapter apiExchangeAdapter,
            UserMarketSettingsRepository userMarketSettingsRepository
    ) {
        List<OrdersBook> ordersBooks = Collections.synchronizedList(new ArrayList<>());
        Set<Exchange> exchanges = AppServiceUtils.getUsersExchanges(userMarketSettingsRepository, exchangeRepository);
        if (exchanges.isEmpty()) return new ArrayList<>();
        Set<String> usersCoinsNames = AppServiceUtils.getUsersCoinsNames(userMarketSettingsRepository);
        ExecutorService executorService = Executors.newFixedThreadPool(exchanges.size());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        long startAsyncOB = System.currentTimeMillis();
        exchanges.forEach(exchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<String> filteredCoinsNames = AppServiceUtils.getFilteredCoinsNames(exchange, usersCoinsNames);
                Set<CoinDepth> coinDepth = apiExchangeAdapter.getOrderBook(exchange, filteredCoinsNames);
                coinDepth.forEach(depth -> {
                    synchronized (ordersBooks) {
                        OrdersBook ordersBook = createOrderBook(exchange, depth);
                        if (ordersBook != null) {
                            ordersBooks.add(ordersBook);
                        }
                    }
                });
            },executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();

        long endAsyncOB = System.currentTimeMillis() - startAsyncOB;
        log.info("Async OB time: {} s", endAsyncOB / 1000);

        return ordersBooks;
    }

    private OrdersBook createOrderBook(Exchange exchange, CoinDepth depth) {
        if (depth == null) return null;
        if (depth.getStatusCode() == 404) return null;

        OrdersBook ordersBook = new OrdersBook();
        ordersBook.setExchange(exchange);
        ordersBook.setCoin(
            exchange.getCoins().stream()
                .filter(coin -> coin.getName().equals(depth.getCoinName()))
                .findFirst().orElseThrow(() -> new RuntimeException("Ошибка в методе getOrderBooks. Монеты нет в списке монет биржи"))
        );

        List<Ask> asks = getAsks(depth, ordersBook).stream().limit(ORDERS_BOOK_SIZE_LIMIT).toList();

        List<Bid> bids = getBids(depth, ordersBook).stream().limit(ORDERS_BOOK_SIZE_LIMIT).toList();

        ordersBook.setAsks(asks);
        ordersBook.setBids(bids);

        return ordersBook;
    }

    private @NotNull List<Ask> getAsks(CoinDepth depth, OrdersBook ordersBook) {
        return depth.getCoinDepthAsks().stream()
            .map(depthAsk -> {
                BigDecimal price = depthAsk.getPrice().setScale(4, RoundingMode.CEILING);
                BigDecimal volume = depthAsk.getVolume().setScale(4, RoundingMode.CEILING);
                if (price.compareTo(BigDecimal.ZERO) <= 0 || volume.compareTo(BigDecimal.ZERO) <= 0) {
                    return null;
                }
                Ask ask = new Ask();
                ask.setOrdersBook(ordersBook);
                ask.setPrice(price);
                ask.setVolume(volume);
                return ask;
            })
            .filter(Objects::nonNull)
            .toList();
    }

    private @NotNull List<Bid> getBids(CoinDepth depth, OrdersBook ordersBook) {
        return depth.getCoinDepthBids().stream()
            .map(depthBid -> {
                BigDecimal price = depthBid.getPrice().setScale(4, RoundingMode.CEILING);
                BigDecimal volume = depthBid.getVolume().setScale(4, RoundingMode.CEILING);
                if (price.compareTo(BigDecimal.ZERO) <= 0 || volume.compareTo(BigDecimal.ZERO) <= 0) {
                    return null;
                }
                Bid bid = new Bid();
                bid.setOrdersBook(ordersBook);
                bid.setPrice(price);
                bid.setVolume(volume);
                return bid;
            })
            .filter(Objects::nonNull)
            .toList();
    }
}
