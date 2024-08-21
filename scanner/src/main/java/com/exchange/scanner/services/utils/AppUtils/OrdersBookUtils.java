package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.*;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

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
     * @getOrderBooksAsync - метод получает список бирж покупки/продажи и в многопоточном режиме
     * передаёт в аргументы метода apiExchangeAdapter.getOrderBook()
     * **/

    @Transactional
    public void getOrderBooks(
            ExchangeRepository exchangeRepository,
            ApiExchangeAdapter apiExchangeAdapter
    ) {
        Set<Exchange> exchanges = new HashSet<>(exchangeRepository.findAll());
        if (exchanges.isEmpty()) return;

        exchanges.forEach(exchange -> {
            Set<Coin> coins = exchange.getCoins();
            apiExchangeAdapter.getOrderBook(exchange, coins);
        });
    }

    /**
     * @createOrderBooks - метод создаёт объекты класса OrdersBook из объекта класса CoinDepth
     * @return - объекты класса OrdersBook
     * **/
    public static OrdersBook createOrderBooks(CoinDepth coinDepth) {
        OrdersBook ordersBook = new OrdersBook();
        if (coinDepth == null || coinDepth.getCoinDepthAsks() == null || coinDepth.getCoinDepthBids() == null) return ordersBook;

        ordersBook.setSlug(coinDepth.getSlug());

        Set<Ask> asks = getAsks(coinDepth, ordersBook);
        Set<Bid> bids = getBids(coinDepth, ordersBook);

        ordersBook.setAsks(asks);
        ordersBook.setBids(bids);
        ordersBook.setFrequencyFactor(0);
        ordersBook.setTimestamp(getTimestamp());

        return ordersBook;
    }

    public static OrdersBook updateOrderBooks(OrdersBook ordersBook, CoinDepth coinDepth) {
        ordersBook.getAsks().clear();
        ordersBook.getBids().clear();
        ordersBook.getAsks().addAll(getAsks(coinDepth, ordersBook));
        ordersBook.getBids().addAll(getBids(coinDepth, ordersBook));
        ordersBook.setFrequencyFactor(ordersBook.getFrequencyFactor() + 1);
        ordersBook.setTimestamp(getTimestamp());
        return ordersBook;
    }

    private static Set<Ask> getAsks(CoinDepth coinDepth, OrdersBook ordersBook) {
        return coinDepth.getCoinDepthAsks().stream().limit(ORDERS_BOOK_SIZE_LIMIT)
                .filter(askResponse -> askResponse.getPrice() != null && askResponse.getVolume() != null)
                .map(askResponse -> {
                    Ask ask = new Ask();
                    ask.setOrdersBook(ordersBook);
                    ask.setPrice(askResponse.getPrice());
                    ask.setVolume(askResponse.getVolume());
                    return ask;
                })
                .collect(Collectors.toSet());
    }

    private static Set<Bid> getBids(CoinDepth coinDepth, OrdersBook ordersBook) {
        return coinDepth.getCoinDepthBids().stream().limit(ORDERS_BOOK_SIZE_LIMIT)
                .filter(bidResponse -> bidResponse.getPrice() != null && bidResponse.getVolume() != null)
                .map(bidResponse -> {
                    Bid bid = new Bid();
                    bid.setOrdersBook(ordersBook);
                    bid.setPrice(bidResponse.getPrice());
                    bid.setVolume(bidResponse.getVolume());
                    return bid;
                })
                .collect(Collectors.toSet());
    }

    private static String getTimestamp() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        return dateFormat.format(System.currentTimeMillis());
    }
}
