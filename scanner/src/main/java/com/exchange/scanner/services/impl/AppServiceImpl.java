package com.exchange.scanner.services.impl;

import com.exchange.scanner.dto.response.event.ArbitrageEvent;
import com.exchange.scanner.dto.response.event.EventData;
import com.exchange.scanner.model.*;
import com.exchange.scanner.repositories.*;
import com.exchange.scanner.security.model.User;
import com.exchange.scanner.security.repository.UserRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;
import com.exchange.scanner.services.AppService;
import com.exchange.scanner.services.utils.AppUtils.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppServiceImpl implements AppService {
    private static final Logger log = LoggerFactory.getLogger(AppServiceImpl.class);

    private final ExchangeRepository exchangeRepository;

    private final UserMarketSettingsRepository userMarketSettingsRepository;

    private final UserRepository userRepository;

    private final ApiExchangeAdapter apiExchangeAdapter;

    private final CoinRepository coinRepository;

    private final OrdersBookRepository ordersBookRepository;

    private final ChainRepository chainRepository;

    private static final int SCHEDULED_RATE_TIME_FOR_REFRESH_COINS = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_CHAINS = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_TRADING_FEE = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_COIN_VOLUME24H = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_ORDERS_BOOK = 5000;

    @Override
    @Transactional
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_REFRESH_COINS)
    public void refreshCoins() {
        long start = System.currentTimeMillis();
        log.info("{} приступил к выполнению задачи refreshCoins", Thread.currentThread().getName());

        RefreshCoinUtils refreshCoinUtils = new RefreshCoinUtils();
        Map<String, Set<Coin>> coins = refreshCoinUtils.getCoinsAsync(exchangeRepository, apiExchangeAdapter);
        if (!coins.isEmpty()) {
            updateCoins(coins);
        } else {
            log.error("Ошибка обновления списка валют. В базе данных нет списка бирж.");
        }

        long end = System.currentTimeMillis() - start;
        log.info("Обновление списка валют успешно завершено. Время выполения: {}s", end / 1000);
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_CHAINS)
    @Transactional
    public void getCoinsChains() {
        long start = System.currentTimeMillis();
        log.info("{} приступил к выполнению задачи getCoinsChains", Thread.currentThread().getName());

        CoinChainUtils coinChainUtils = new CoinChainUtils();
        Map<String, Set<Coin>> coinsMap = coinChainUtils.getCoinsChainInfoAsync(apiExchangeAdapter, exchangeRepository, userMarketSettingsRepository);
        coinsMap.forEach((exchange, coins) -> {
//            System.out.println("exchange: " + exchange);
//            coins.forEach(coin -> {
//                System.out.println("coin: " + coin.getName());
//                System.out.println("chains:");
//                coin.getChains().forEach(ch -> {
//                    System.out.println(ch.getName() + " " + ch.getCommission());
//                });
//            });
            coinRepository.saveAll(coins);
        });

        long end = System.currentTimeMillis() - start;
        log.info("Обновление списка сетей успешно завершено. Время выполнения {}s", end / 1000);
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_TRADING_FEE)
    @Transactional
    public void getTradingFee() {
        long start = System.currentTimeMillis();
        log.info("{} приступил к выполнению задачи getTradingFee", Thread.currentThread().getName());

        TradingFeeUtils tradingFeeUtils = new TradingFeeUtils();
        Map<String, Set<Coin>> coinsMap = tradingFeeUtils.getTradingFeeAsync(apiExchangeAdapter, exchangeRepository, userMarketSettingsRepository);
        coinsMap.forEach((exchange, coins) -> {
//            System.out.println("Exchange: " + exchange);
//            coins.forEach(coin -> {
//                System.out.println(coin.getName());
//                System.out.println(coin.getTakerFee());
//            });
            coinRepository.saveAll(coins);
        });

        long end = System.currentTimeMillis() - start;
        log.info("Обновление торговых комиссий успешно завершено. Время выполнения {}s", end / 1000);
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_COIN_VOLUME24H)
    @Transactional
    public void getVolume24h() {
        long start = System.currentTimeMillis();
        log.info("{} приступил к выполнению задачи getVolume24h", Thread.currentThread().getName());

        RefreshVolume24hUtils refreshVolume24hUtils = new RefreshVolume24hUtils();
        Map<String, Set<Coin>> coinsMap = refreshVolume24hUtils.getVolume24hAsync(apiExchangeAdapter, exchangeRepository, userMarketSettingsRepository);

        coinsMap.forEach((exchange, coins) -> {
            coinRepository.saveAll(coins);
        });

        long end = System.currentTimeMillis() - start;
        log.info("Обновление суточного торгового объёма успешно завершено. Время выполнения {}s", end / 1000);
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_ORDERS_BOOK)
    @Transactional
    public void getOrderBooks() {
        long start = System.currentTimeMillis();
        log.info("{} приступил к выполнению задачи getOrderBooks", Thread.currentThread().getName());

        OrdersBookUtils ordersBookUtils = new OrdersBookUtils();
        List<OrdersBook> ordersBooks = ordersBookUtils.getOrderBooksAsync(
                exchangeRepository,
                apiExchangeAdapter,
                userMarketSettingsRepository
        );

        ordersBookRepository.deleteAllInBatch();
        ordersBookRepository.saveAll(ordersBooks);

        long end = System.currentTimeMillis() - start;
        log.info("Операция обновления стакана цен выполнена. Время выполнения: {}s", end / 1000);
    }

    @Transactional
    protected void updateCoins(Map<String, Set<Coin>> coinsMap) {
        Set<Exchange> exchangesToUpdate = new HashSet<>();
        coinsMap.forEach((exchangeName, coins) -> {
            Exchange exchange = exchangeRepository.findByName(exchangeName);
            Set<Coin> coinsToDelete =  exchange.getCoins().stream()
                    .filter(coin -> !coins.contains(coin))
                    .collect(Collectors.toSet());
            coinRepository.deleteAllInBatch(coinsToDelete);
            exchange.getCoins().removeAll(coinsToDelete);
            exchange.setCoins(coins);
            exchangesToUpdate.add(exchange);
        });

        exchangeRepository.saveAll(exchangesToUpdate);
    }

    @Transactional
    protected UserMarketSettings createUserMarketSettingsWithDefaults(User user) {
        List<String> exchangesNames = exchangeRepository.findAll().stream().map(Exchange::getName).toList();
        var userMarketSettings = UserMarketSettingsBuilder.getDefaultUserMarketSettings(user, exchangesNames);

        return userMarketSettingsRepository.save(userMarketSettings);
    }

    @Override
    @Transactional
    public CompletableFuture<Set<String>> getExchanges() {
        return CompletableFuture.supplyAsync(() -> {
            return exchangeRepository.findAll().stream().map(Exchange::getName).collect(Collectors.toSet());
        });
    }

    @Override
    @Transactional
    public CompletableFuture<List<ArbitrageEvent>> getArbitrageOpportunities(UserDetails userDetails) {
        return CompletableFuture.supplyAsync(() -> {
            if (userDetails == null) {
                return new ArrayList<>();
            }

            var user = userRepository.findByUsername(userDetails.getUsername()).orElseThrow(() ->
                    new UsernameNotFoundException("Пользователь не зарегистрирован")
            );

            UserMarketSettings userMarketSettings;
            Optional<UserMarketSettings> optional = userMarketSettingsRepository.getByUser(user);
            userMarketSettings = optional.orElseGet(() -> createUserMarketSettingsWithDefaults(user));

            List<ArbitrageEvent> arbitrageEvents = new ArrayList<>();
            ArbitrageOpportunitiesUtils arbitrageOpportunitiesUtils = new ArbitrageOpportunitiesUtils();
            Map<String, List<ArbitrageOpportunity>> arbitrageOpportunities = arbitrageOpportunitiesUtils
                    .checkExchangesForArbitrageOpportunities(userMarketSettings, ordersBookRepository);

            arbitrageOpportunities.forEach((coinName, arbitrageOpportunitiesList) -> {

                ArbitrageEvent arbitrageEvent = new ArbitrageEvent();
                List<EventData> eventDataList = arbitrageOpportunitiesUtils.getEventDataFromArbitrageOpportunities(arbitrageOpportunitiesList, userMarketSettings);
                arbitrageEvent.setEventData(eventDataList);

                if (!arbitrageEvent.getEventData().isEmpty()) {
                    arbitrageEvent.setCoin(coinName);
                    arbitrageEvents.add(arbitrageEvent);
                }
            });


            return arbitrageEvents;
        });
    }
}
