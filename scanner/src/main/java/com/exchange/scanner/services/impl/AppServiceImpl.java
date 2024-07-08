package com.exchange.scanner.services.impl;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
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
@Transactional
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
    public void getCoinsChains() {
        long start = System.currentTimeMillis();
        log.info("{} приступил к выполнению задачи getCoinsChains", Thread.currentThread().getName());

        CoinChainUtils coinChainUtils = new CoinChainUtils();
        Set<ChainResponseDTO> response = coinChainUtils.getCoinsChainInfoAsync(
                apiExchangeAdapter,
                exchangeRepository,
                userMarketSettingsRepository
        );

        response.forEach(chainResponse -> {
            chainResponse.getCoin().setChains(chainResponse.getChains());
        });

        long end = System.currentTimeMillis() - start;
        log.info("Обновление списка сетей успешно завершено. Время выполнения {}s", end / 1000);
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_TRADING_FEE)
    public void getTradingFee() {
        long start = System.currentTimeMillis();
        log.info("{} приступил к выполнению задачи getTradingFee", Thread.currentThread().getName());

        TradingFeeUtils tradingFeeUtils = new TradingFeeUtils();
        Set<TradingFeeResponseDTO> tradingResponse = tradingFeeUtils.getTradingFeeAsync(
                apiExchangeAdapter,
                exchangeRepository,
                userMarketSettingsRepository
        );
        tradingResponse.forEach(response -> {
            response.getCoin().setTakerFee(response.getTradingFee());
        });

        long end = System.currentTimeMillis() - start;
        log.info("Обновление торговых комиссий успешно завершено. Время выполнения {}s", end / 1000);
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_COIN_VOLUME24H)
    public void getVolume24h() {
        long start = System.currentTimeMillis();
        log.info("{} приступил к выполнению задачи getVolume24h", Thread.currentThread().getName());

        RefreshVolume24hUtils refreshVolume24hUtils = new RefreshVolume24hUtils();
        Set<Volume24HResponseDTO> volume24HResponse = refreshVolume24hUtils.getVolume24hAsync(
                apiExchangeAdapter,
                exchangeRepository,
                userMarketSettingsRepository
        );
        volume24HResponse.forEach(volume24H -> {
            volume24H.getCoin().setVolume24h(volume24H.getVolume24H());
        });

        long end = System.currentTimeMillis() - start;
        log.info("Обновление суточного торгового объёма успешно завершено. Время выполнения {}s", end / 1000);
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_ORDERS_BOOK)
    public void getOrderBooks() {
//        long start = System.currentTimeMillis();
//        log.info("{} приступил к выполнению задачи getOrderBooks", Thread.currentThread().getName());

        OrdersBookUtils ordersBookUtils = new OrdersBookUtils();
        List<OrdersBook> ordersBooks = ordersBookUtils.getOrderBooksAsync(
                exchangeRepository,
                apiExchangeAdapter,
                userMarketSettingsRepository
        );

        ordersBookRepository.deleteAllInBatch();
        ordersBookRepository.saveAll(ordersBooks);

//        long end = System.currentTimeMillis() - start;
//        log.info("Операция обновления стакана цен выполнена. Время выполнения: {}s", end / 1000);
    }

    protected void updateCoins(Map<String, Set<Coin>> coinsMap) {
        coinsMap.forEach((exchangeName, coins) -> {
            Exchange exchange = exchangeRepository.findByName(exchangeName);
            Set<Coin> coinsToDelete =  exchange.getCoins().stream()
                    .filter(coin -> !coins.contains(coin))
                    .collect(Collectors.toSet());
            exchange.getCoins().removeAll(coinsToDelete);
            Set<Coin> coinsToUpdate = coins.stream()
                    .filter(updatedCoin -> !exchange.getCoins().contains(updatedCoin))
                    .collect(Collectors.toSet());
            exchange.getCoins().addAll(coinsToUpdate);
        });
    }

    protected UserMarketSettings createUserMarketSettingsWithDefaults(User user) {
        List<String> exchangesNames = exchangeRepository.findAll().stream().map(Exchange::getName).toList();
        var userMarketSettings = UserMarketSettingsBuilder.getDefaultUserMarketSettings(user, exchangesNames);

        return userMarketSettingsRepository.save(userMarketSettings);
    }

    @Override
    public CompletableFuture<Set<String>> getExchanges() {
        return CompletableFuture.supplyAsync(() -> {
            return exchangeRepository.findAll().stream().map(Exchange::getName).collect(Collectors.toSet());
        });
    }

    @Override
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
