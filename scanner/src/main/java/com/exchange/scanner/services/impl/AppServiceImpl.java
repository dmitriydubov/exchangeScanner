package com.exchange.scanner.services.impl;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.event.ArbitrageEvent;
import com.exchange.scanner.dto.response.CoinInfoDTO;
import com.exchange.scanner.dto.response.event.ArbitrageOpportunity;
import com.exchange.scanner.dto.response.event.UserTradeEvent;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.*;
import com.exchange.scanner.repositories.*;
import com.exchange.scanner.security.model.User;
import com.exchange.scanner.security.repository.UserRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;
import com.exchange.scanner.services.AppService;
import com.exchange.scanner.services.ArbitrageService;
import com.exchange.scanner.services.CoinMarketCapService;
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

    private final OrdersBookRepository ordersBookRepository;

    private final CoinRepository coinRepository;

    private final CoinMarketCapService coinMarketCapService;

    private final ArbitrageService arbitrageService;

    private static final int SCHEDULED_RATE_TIME_FOR_REFRESH_COINS = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_CHAINS = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_TRADING_FEE = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_COIN_VOLUME24H = 1000 * 60 * 60 * 24;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_ORDERS_BOOK = 5000;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_COIN_INFO = 1000 * 60 * 60;

    @Override
//    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_REFRESH_COINS)
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
//    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_CHAINS, initialDelay = 1000)
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
//    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_TRADING_FEE, initialDelay = 2000)
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
//    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_COIN_VOLUME24H, initialDelay = 3000)
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
//    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_COIN_INFO, initialDelay = 4000)
    public void getCoinMarketCapCoinInfo() {
        long start = System.currentTimeMillis();
        log.info("{} приступил к выполнению задачи getCoinMarketCapCoinInfo", Thread.currentThread().getName());

        CoinMarketCapUtils coinMarketCapUtils = new CoinMarketCapUtils();
        Set<CoinInfoDTO> response = coinMarketCapUtils.getCoinMarketCapCoinInfo(
                exchangeRepository,
                coinMarketCapService,
                userMarketSettingsRepository);

        response.forEach(coinResponse -> {
            Coin coin = coinResponse.getCoin();
            coin.setCoinMarketCapLink(coinResponse.getCoinMarketCapLink() + "/" + coinResponse.getSlug() + "/");
            coin.setLogoLink(coinResponse.getLogoLink());
        });

        long end = System.currentTimeMillis() - start;
        log.info("Обновление информации о монете от coinmarketcap успешно заврешено. Время выполнения {}s", end / 1000);
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_ORDERS_BOOK, initialDelay = 5000)
    public void getOrderBooks() {
        long start = System.currentTimeMillis();
        log.info("{} приступил к выполнению задачи getOrderBooks", Thread.currentThread().getName());

        ordersBookRepository.deleteAll();
        OrdersBookUtils ordersBookUtils = new OrdersBookUtils();
        Set<CoinDepth> coinDepthSet = ordersBookUtils.getOrderBooksAsync(
                exchangeRepository,
                apiExchangeAdapter,
                userMarketSettingsRepository
        );
        coinDepthSet.forEach(depth -> {
            OrdersBook ordersBook = ordersBookUtils.createOrderBooks(depth);
            ordersBook.setSlug(depth.getSlug());
            Coin coin = depth.getCoin();
            ordersBook.setCoin(coin);
            OrdersBook savedOrderBook = ordersBookRepository.save(ordersBook);
            coin.setOrdersBook(savedOrderBook);
        });


        long end = System.currentTimeMillis() - start;
        log.info("Операция обновления стакана цен выполнена. Время выполнения: {}s", end / 1000);
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

    protected synchronized UserMarketSettings createUserMarketSettingsWithDefaults(User user) {
        List<String> exchangesNames = exchangeRepository.findAll().stream().map(Exchange::getName).toList();
        var userMarketSettings = UserMarketSettingsBuilder.getDefaultUserMarketSettings(user, exchangesNames);

        return userMarketSettingsRepository.save(userMarketSettings);
    }

    @Override
    public CompletableFuture<Set<String>> getExchanges() {
        return CompletableFuture.supplyAsync(() -> exchangeRepository
                .findAll().stream()
                .map(Exchange::getName)
                .collect(Collectors.toSet())
        );
    }

    @Override
    public CompletableFuture<Set<ArbitrageEvent>> getArbitrageOpportunities(UserDetails userDetails) {
        return CompletableFuture.supplyAsync(() -> {
            if (userDetails == null) {
                return new HashSet<>();
            }

            var user = userRepository.findByUsername(userDetails.getUsername()).orElseThrow(() ->
                    new UsernameNotFoundException("Пользователь не зарегистрирован")
            );

            UserMarketSettings userMarketSettings;
            Optional<UserMarketSettings> optional = userMarketSettingsRepository.getByUser(user);
            userMarketSettings = optional.orElseGet(() -> createUserMarketSettingsWithDefaults(user));

            ArbitrageUtils arbitrageUtils = new ArbitrageUtils();
            UserTradeEvent userTradeEvent = arbitrageUtils.createUserTradeEvent(
                    exchangeRepository,
                    ordersBookRepository,
                    coinRepository,
                    userMarketSettings
            );

            Set<ArbitrageOpportunity> arbitrageOpportunities = arbitrageService.getArbitrageOpportunities(userTradeEvent);

            return Set.of();
        });
    }
}
