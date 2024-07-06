package com.exchange.scanner.services.utils.ordersbook;

import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Ask;
import com.exchange.scanner.model.Bid;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.model.OrdersBook;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public class OrdersBookUtils {

    public static OrdersBook createOrderBook(Exchange exchange, CoinDepth depth) {
        if (depth == null) return null;
        if (depth.getStatusCode() == 404) return null;

        OrdersBook ordersBook = new OrdersBook();
        ordersBook.setExchange(exchange);
        ordersBook.setCoin(
                exchange.getCoins().stream()
                        .filter(coin -> coin.getName().equals(depth.getCoinName()))
                        .findFirst().orElseThrow(() -> new RuntimeException("Ошибка в методе getOrderBooks. Монеты нет в списке монет биржи"))
        );

        List<Bid> bids = OrdersBookUtils.getBids(depth, ordersBook);

        List<Ask> asks = OrdersBookUtils.getAsks(depth, ordersBook);

        ordersBook.setBids(bids);
        ordersBook.setAsks(asks);

        return ordersBook;
    }

    private static @NotNull List<Ask> getAsks(CoinDepth depth, OrdersBook ordersBook) {
        return depth.getCoinDepthAsks().stream()
            .map(depthAsk -> {
                BigDecimal price = new BigDecimal(depthAsk.getPrice()).setScale(4, RoundingMode.CEILING);
                BigDecimal volume = new BigDecimal(depthAsk.getVolume()).setScale(4, RoundingMode.CEILING);
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

    private static @NotNull List<Bid> getBids(CoinDepth depth, OrdersBook ordersBook) {
        return depth.getCoinDepthBids().stream()
            .map(depthBid -> {
                BigDecimal price = new BigDecimal(depthBid.getPrice()).setScale(4, RoundingMode.CEILING);
                BigDecimal volume = new BigDecimal(depthBid.getVolume()).setScale(4, RoundingMode.CEILING);
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
