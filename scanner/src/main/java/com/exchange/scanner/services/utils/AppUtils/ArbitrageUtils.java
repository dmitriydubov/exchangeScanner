package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.dto.response.event.*;
import com.exchange.scanner.model.*;
import com.exchange.scanner.repositories.*;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ArbitrageUtils {

    public UserTradeEvent createUserTradeEvent(
            ExchangeRepository exchangeRepository,
            OrdersBookRepository ordersBookRepository,
            AskRepository askRepository,
            BidRepository bidRepository,
            CoinRepository coinRepository,
            ReentrantLock lock
    ) {
        UserTradeEvent userTradeEvent = new UserTradeEvent();
        Set<String> coinNames = coinRepository.findAll().stream().map(Coin::getName).collect(Collectors.toSet());
        Set<Exchange> marketsForBuy = new HashSet<>(exchangeRepository.findAll());
        Set<Exchange> marketsForSell = new HashSet<>(exchangeRepository.findAll());

        try {
            lock.lock();
            Set<UserBuyTradeEventDTO> buyTradeEventDTO = generateTradeEvents(
                    marketsForBuy,
                    coinNames,
                    ordersBookRepository,
                    askRepository,
                    bidRepository,
                    coinRepository,
                    UserBuyTradeEventDTO.class, true);

            Set<UserSellTradeEventDTO> sellTradeEventDTO = generateTradeEvents(
                    marketsForSell,
                    coinNames,
                    ordersBookRepository,
                    askRepository,
                    bidRepository,
                    coinRepository,
                    UserSellTradeEventDTO.class,
                    false);

            userTradeEvent.setBuyTradeEventDTO(new TreeSet<>(buyTradeEventDTO));
            userTradeEvent.setSellTradeEventDTO(new TreeSet<>(sellTradeEventDTO));

            return userTradeEvent;
        } finally {
            lock.unlock();
        }
    }

    private <T extends TradeEventDTO> Set<T> generateTradeEvents(
            Set<Exchange> markets,
            Set<String> coinNames,
            OrdersBookRepository ordersBookRepository,
            AskRepository askRepository,
            BidRepository bidRepository,
            CoinRepository coinRepository,
            Class<T> tradeEventDTOClass,
            boolean isBuy
    ) {
        Map<String, Coin> coinsMap = coinRepository.findAll().stream()
                .collect(Collectors.toMap(Coin::getSlug, coin -> coin));
        Map<String, OrdersBook> ordersBookMap = ordersBookRepository.findAll().stream()
                .filter(ordersBook -> {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                    try {
                        return checkPriceDateCreation(ordersBook, dateFormat);
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toMap(OrdersBook::getSlug, ordersBook -> ordersBook));
        Map<String, List<Ask>> askMap = askRepository.findAll().stream()
                .collect(Collectors.groupingBy(ask -> ask.getOrdersBook().getId().toString()));
        Map<String, List<Bid>> bidMap = bidRepository.findAll().stream()
                .collect(Collectors.groupingBy(bid -> bid.getOrdersBook().getId().toString()));

        return markets.stream()
            .filter(market -> !market.getIsBlockBySuperuser())
            .flatMap(market -> {
                Set<Coin> filteredCoins = AppServiceUtils.getFilteredCoins(market, coinNames);
                return filteredCoins != null ?
                        filteredCoins.stream().map(coin -> new MarketCoinPair(market, coin)) :
                        Stream.empty();
            })
            .filter(pair -> !pair.coin.getIsBlockBySuperuser())
            .map(pair -> {
                OrdersBook ordersBook = ordersBookMap.get(pair.coin.getSlug());
                if (ordersBook == null) return null;
                T tradeEventDTO = getUserTradeEventDTO(
                        pair.market, pair.coin, coinsMap, tradeEventDTOClass
                );
                if (isBuy) {
                    tradeEventDTO.setAsks(new TreeSet<>(askMap.get(ordersBook.getId().toString())));
                } else {
                    tradeEventDTO.setBids(new TreeSet<>(bidMap.get(ordersBook.getId().toString())));
                }

                return tradeEventDTO;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private static boolean checkPriceDateCreation(OrdersBook ordersBook, SimpleDateFormat dateFormat) throws ParseException {
        Date date = dateFormat.parse(ordersBook.getTimestamp());
        Calendar calendarPriceCreation = Calendar.getInstance();
        calendarPriceCreation.setTime(date);
        Calendar calendarDateNow = Calendar.getInstance();
        calendarDateNow.setTime(new Date());
        long millisPriceCreation = calendarPriceCreation.getTimeInMillis();
        long millisNow = calendarDateNow.getTimeInMillis();
        return millisNow - millisPriceCreation < 1_800_000L;
    }

    private static <T extends TradeEventDTO> T getUserTradeEventDTO(
            Exchange exchange,
            Coin coin,
            Map<String, Coin> coinsMap,
            Class<T> classDTO
    ) {
        try {
            T tradeEventDTO = classDTO.getConstructor().newInstance();
            Optional<Coin> coinOptional = Optional.ofNullable(coinsMap.get(coin.getSlug()));
            if (coinOptional.isEmpty()) return tradeEventDTO;
            Set<Chain> chains = coinOptional.get().getChains();
            tradeEventDTO.setExchange(exchange.getName());
            tradeEventDTO.setCoin(coin.getName());
            tradeEventDTO.setDepositLink(coin.getDepositLink());
            tradeEventDTO.setWithdrawLink(coin.getWithdrawLink());
            tradeEventDTO.setTradeLink(coin.getTradeLink());
            tradeEventDTO.setCoinMarketCapLink(coin.getCoinMarketCapLink());
            tradeEventDTO.setLogoLink(coin.getLogoLink());
            tradeEventDTO.setTakerFee(coin.getTakerFee());
            tradeEventDTO.setVolume24h(coin.getVolume24h());
            tradeEventDTO.setChains(chains);
            tradeEventDTO.setIsMargin(coin.getIsMarginTradingAllowed());
            return tradeEventDTO;
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            log.info("Ошибка в методе getUserTradeEventDTO");
            throw new RuntimeException(e);
        }
    }

    private record MarketCoinPair(Exchange market, Coin coin) {
    }
}
