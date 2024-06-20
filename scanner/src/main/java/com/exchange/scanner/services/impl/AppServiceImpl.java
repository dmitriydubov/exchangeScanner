package com.exchange.scanner.services.impl;

import com.exchange.scanner.dto.response.SimpleResponse;
import com.exchange.scanner.dto.response.event.ArbitrageEvent;
import com.exchange.scanner.dto.response.event.EventData;
import com.exchange.scanner.model.*;
import com.exchange.scanner.repositories.CoinRepository;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;
import com.exchange.scanner.security.model.User;
import com.exchange.scanner.security.repository.UserRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;
import com.exchange.scanner.services.AppService;
import com.exchange.scanner.services.utils.AppServiceUtils;
import com.exchange.scanner.services.utils.UserMarketSettingsBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AppServiceImpl implements AppService {

    private final ExchangeRepository exchangeRepository;

    private final UserMarketSettingsRepository userMarketSettingsRepository;

    private final UserRepository userRepository;

    private final ApiExchangeAdapter apiExchangeAdapter;

    private final CoinRepository coinRepository;

    private final OrdersBookRepository ordersBookRepository;
    
    private static final int SCHEDULED_RATE_TIME_FOR_GET_ORDERS = 5000;

    @Override
    public Set<Exchange> getExchanges() {
        return new HashSet<>(exchangeRepository.findAll());
    }

    @Override
    @Transactional
    public ArbitrageEvent getArbitrageOpportunities(UserDetails userDetails) {
        var user = userRepository.findByUsername(userDetails.getUsername()).orElseThrow(() ->
                new UsernameNotFoundException("Пользователь не зарегистрирован")
        );
        UserMarketSettings userMarketSettings;
        Optional<UserMarketSettings> optional = userMarketSettingsRepository.getByUser(user);
        userMarketSettings = optional.orElseGet(() -> createUserMarketSettingsWithDefaults(user));

        userMarketSettings.getCoins().forEach(coinName -> {
            List<OrdersBook> ordersBooks = ordersBookRepository.findByCoinName(coinName);
            Map<String, Set<Ask>> lowestBuyPrice = AppServiceUtils.getBuyPrices(ordersBooks);
            Map<String, Set<Bid>> highestSellPrice = AppServiceUtils.getSellPrices(ordersBooks);

            System.out.println("buy");
            System.out.println(coinName);
            lowestBuyPrice.forEach((ex, ask) -> {
                System.out.println(ex);
                ask.forEach(a -> System.out.println(a.getPrice() + " " + a.getVolume()));
            });

            System.out.println("sell");
            System.out.println(coinName);
            highestSellPrice.forEach((ex, bid) -> {
                System.out.println(ex);
                bid.forEach(b -> System.out.println(b.getPrice() + " " + b.getVolume()));
            });

//            lowestBuyPrice.forEach((exchangeForBuy, ask) -> {
//                highestSellPrice.forEach((exchangeForSell, bid) -> {
//                    if (!exchangeForBuy.equals(exchangeForSell)) {
//                        BigDecimal bidPriceValue = new BigDecimal(bid.getPrice());
//                        BigDecimal askPriceValue = new BigDecimal(ask.getPrice());
//                        BigDecimal spread = bidPriceValue.subtract(askPriceValue);
//                        if (spread.compareTo(new BigDecimal(0)) > 0) {
//                            System.out.println("is spread for " + coinName + ". Exchange for buy: " + exchangeForBuy + ". Exchange for sell: " + exchangeForSell);
//                            BigDecimal bidVolume = new BigDecimal(bid.getVolume());
//                            BigDecimal askVolume = new BigDecimal(ask.getVolume());
//                            BigDecimal tradeVolume = bidVolume.subtract(askVolume);
//                            tradeVolume = tradeVolume.compareTo(new BigDecimal(0)) > 0 ?
//                                    tradeVolume :
//                                    bidVolume;
//                            System.out.println("trade volume: " + tradeVolume);
//                            BigDecimal fiatVolumeForBuy = tradeVolume.multiply(askPriceValue);
//                            System.out.println("fiat volume for buy: " + fiatVolumeForBuy);
//                            BigDecimal fiatVolumeForSell = tradeVolume.multiply(bidPriceValue);
//                            System.out.println("fiat volume for sell: " + fiatVolumeForSell);
//                            BigDecimal profit = fiatVolumeForSell.subtract(fiatVolumeForBuy);
//                            System.out.println("///////////");
//                            System.out.println("profit " + profit);
//                            System.out.println("///////////");
//                        }
//                    }
//                });
//            });
        });


        ArbitrageEvent arbitrageEvent = new ArbitrageEvent();
        List<EventData> eventData = new ArrayList<>();
        arbitrageEvent.setEventData(eventData);
        return new ArbitrageEvent();
    }

    @Override
//    @Scheduled(fixedRate = 1000 * 60 * 60)
    public SimpleResponse refreshCoins() {
        Map<String, Set<Coin>> updatedCoinsMap = AppServiceUtils.getCoinsAsync(exchangeRepository, apiExchangeAdapter);
        updateCoins(updatedCoinsMap);

        return new SimpleResponse("Обновление списка валют успешно завершено");
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_ORDERS)
    @Transactional
    public void getOrderBooks() {
        long start = System.currentTimeMillis();
        List<OrdersBook> ordersBooks = AppServiceUtils.getOrderBooksAsync(
                exchangeRepository,
                apiExchangeAdapter,
                userMarketSettingsRepository
        );

        ordersBookRepository.deleteAllInBatch();
        ordersBookRepository.saveAll(ordersBooks);

        long end = System.currentTimeMillis() - start;
//        System.out.println("Операция выполнена за " + end / 1000 + "s");
    }

    @Transactional
    private UserMarketSettings createUserMarketSettingsWithDefaults(User user) {
        List<String> exchangesNames = exchangeRepository.findAll().stream().map(Exchange::getName).toList();
        var userMarketSettings = UserMarketSettingsBuilder.getDefaultUserMarketSettings(user, exchangesNames);

        return userMarketSettingsRepository.save(userMarketSettings);
    }

    @Transactional
    private void updateCoins(Map<String, Set<Coin>> coinsMap) {
        Set<Exchange> exchangesToUpdate = new HashSet<>();
        coinsMap.forEach((exchangeName, coins) -> {
            Exchange exchange = exchangeRepository.findByName(exchangeName);
            exchange.getCoins().clear();
            exchange.setCoins(coins);
            exchangesToUpdate.add(exchange);
        });

        coinRepository.deleteAllInBatch();
        exchangeRepository.saveAll(exchangesToUpdate);
    }
}
