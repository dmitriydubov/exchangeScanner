package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.dto.response.event.*;
import com.exchange.scanner.model.*;
import com.exchange.scanner.repositories.CoinRepository;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.OrdersBookRepository;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ArbitrageUtils {

    public UserTradeEvent createUserTradeEvent(
            ExchangeRepository exchangeRepository,
            OrdersBookRepository ordersBookRepository,
            CoinRepository coinRepository,
            UserMarketSettings userMarketSettings
    ) {
        UserTradeEvent userTradeEvent = new UserTradeEvent();
        Set<String> userCoinNames = new HashSet<>(userMarketSettings.getCoins());
        Set<Exchange> usersMarketsForBuy = exchangeRepository.findAllByNameIn(userMarketSettings.getMarketsBuy());
        Set<Exchange> userMarketsForSell = exchangeRepository.findAllByNameIn(userMarketSettings.getMarketsSell());

        userTradeEvent.setBuyTradeEventDTO(generateTradeEvents(
                usersMarketsForBuy,
                userCoinNames,
                userMarketSettings,
                ordersBookRepository,
                coinRepository,
                UserBuyTradeEventDTO.class, true)
        );
        userTradeEvent.setSellTradeEventDTO(generateTradeEvents(
                userMarketsForSell,
                userCoinNames,
                userMarketSettings,
                ordersBookRepository,
                coinRepository,
                UserSellTradeEventDTO.class,
                false)
        );

        return userTradeEvent;
    }

    private <T extends TradeEventDTO> Set<T> generateTradeEvents(
            Set<Exchange> markets,
            Set<String> userCoinNames,
            UserMarketSettings userMarketSettings,
            OrdersBookRepository ordersBookRepository,
            CoinRepository coinRepository,
            Class<T> tradeEventDTOClass,
            boolean isBuy
    ) {
        return markets.parallelStream()
                .filter(market -> !market.getIsBlockBySuperuser())
                .flatMap(market -> {
                    Set<Coin> filteredCoins = AppServiceUtils.getFilteredCoins(market, userCoinNames);
                    return filteredCoins != null ?
                            filteredCoins.stream().map(coin -> new MarketCoinPair(market, coin)) :
                            Stream.empty();
                })
                .filter(pair -> !pair.coin.getIsBlockBySuperuser())
                .map(pair -> {
                    OrdersBook ordersBook = ordersBookRepository.findBySlug(pair.coin.getSlug()).orElse(null);
                    if (ordersBook == null) return null;
                    T tradeEventDTO = getUserTradeEventDTO(pair.market, pair.coin, coinRepository, userMarketSettings, tradeEventDTOClass);
                    if (isBuy) {
                        tradeEventDTO.setAsks(new TreeSet<>(ordersBook.getAsks()));
                    } else {
                        tradeEventDTO.setBids(new TreeSet<>(ordersBook.getBids()));
                    }
                    return tradeEventDTO;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static @NotNull <T extends TradeEventDTO> T getUserTradeEventDTO(
            Exchange exchange,
            Coin coin,
            CoinRepository coinRepository,
            UserMarketSettings userMarketSettings,
            Class<T> classDTO
    ) {
        try {
            T tradeEventDTO = classDTO.getConstructor().newInstance();
            Coin coinEntity = coinRepository.findBySlug(coin.getSlug()).orElse(null);
            if (coinEntity == null) return tradeEventDTO;
            Set<Chain> chains = coinEntity.getChains();
            tradeEventDTO.setExchange(exchange.getName());
            tradeEventDTO.setCoin(coin.getName());
            tradeEventDTO.setDepositLink(coin.getDepositLink());
            tradeEventDTO.setWithdrawLink(coin.getWithdrawLink());
            tradeEventDTO.setTradeLink(coin.getTradeLink());
            tradeEventDTO.setCoinMarketCapLink(coin.getCoinMarketCapLink());
            tradeEventDTO.setLogoLink(coin.getLogoLink());
            tradeEventDTO.setTakerFee(coin.getTakerFee());
            tradeEventDTO.setVolume24h(coin.getVolume24h());
            tradeEventDTO.setMinUserTradeAmount(userMarketSettings.getMinVolume());
            tradeEventDTO.setMaxUserTradeAmount(userMarketSettings.getMaxVolume());
            tradeEventDTO.setUserMinProfit(userMarketSettings.getProfitSpread());
            tradeEventDTO.setChains(chains);
            return tradeEventDTO;
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            log.info("Ошибка в методе getUserTradeEventDTO");
            throw new RuntimeException(e);
        }
    }

    private record MarketCoinPair(Exchange market, Coin coin) {
    }
}
