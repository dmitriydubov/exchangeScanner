package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.*;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *@OrdersBookUtils - Утилитный класс для получения dto (CoinDepth) ответов от бирж с данными о стаканах цен (depth) и обновление
 * объектов OrdersBook (стаканы цен) для каждой монеты
 * **/

@Slf4j
public class OrdersBookUtils {

    /**
     * @ORDERS_BOOK_SIZE_LIMIT - лимит количества создания объектов ask/bid в OrdersBook из объекта CoinDepth. Количество
     * ask и bid в ответе от api бирж отличается. Данная переменная структурирует ask/bid к единому количеству в базе данных
     * для каждого объекта OrdersBook
     * **/

    private static final int ORDERS_BOOK_SIZE_LIMIT = 10;

    /**
     * @getOrderBooksAsync - метод получает список бирж покупки/продажи, имена монет пользователя и в многопоточном режиме
     * передаёт в аргументы метода apiExchangeAdapter.getOrderBook()
     * @return - сет объектов класса CoinDepth
     * **/

    public Set<CoinDepth> getOrderBooksAsync(
            ExchangeRepository exchangeRepository,
            ApiExchangeAdapter apiExchangeAdapter,
            UserMarketSettingsRepository userMarketSettingsRepository
    ) {
        Set<CoinDepth> result = Collections.synchronizedSet(new HashSet<>());
        Set<Exchange> exchanges = AppServiceUtils.getUsersExchanges(userMarketSettingsRepository, exchangeRepository);
        if (exchanges.isEmpty()) return new HashSet<>();
        Set<String> usersCoinsNames = AppServiceUtils.getUsersCoinsNames(userMarketSettingsRepository);
        ExecutorService executorService = Executors.newFixedThreadPool(exchanges.size());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        exchanges.forEach(exchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<Coin> filteredCoins = AppServiceUtils.getFilteredCoins(exchange, usersCoinsNames);
                Set<CoinDepth> coinDepth = apiExchangeAdapter.getOrderBook(exchange, filteredCoins);
                coinDepth.forEach(depth -> {
                    synchronized (result) {
                        result.addAll(coinDepth);
                    }
                });
            },executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();

        return result;
    }

    /**
     * @createOrderBooks - метод создаёт объекты класса OrdersBook из объекта класса CoinDepth
     * @return - объекты класса OrdersBook
     * **/
    public OrdersBook createOrderBooks(CoinDepth coinDepth) {
        OrdersBook ordersBook = new OrdersBook();
        if (coinDepth == null || coinDepth.getCoinDepthAsks() == null || coinDepth.getCoinDepthBids() == null) return ordersBook;

        ordersBook.setSlug(coinDepth.getSlug());

        Set<Ask> asks = new TreeSet<>();
        coinDepth.getCoinDepthAsks().stream().limit(ORDERS_BOOK_SIZE_LIMIT).forEach(askResponse -> {
            Ask ask = new Ask();
            ask.setPrice(askResponse.getPrice());
            ask.setVolume(askResponse.getVolume());
            asks.add(ask);
        });

        Set<Bid> bids = new TreeSet<>();
        coinDepth.getCoinDepthBids().stream().limit(ORDERS_BOOK_SIZE_LIMIT).forEach(bidResponse -> {
            Bid bid = new Bid();
            bid.setPrice(bidResponse.getPrice());
            bid.setVolume(bidResponse.getVolume());
            bids.add(bid);
        });

        ordersBook.setAsks(asks);
        ordersBook.setBids(bids);

        return ordersBook;
    }
}
