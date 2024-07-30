package com.exchange.scanner.services.impl;

import com.exchange.scanner.dto.request.UserUpdateMarketData;
import com.exchange.scanner.dto.response.*;
import com.exchange.scanner.dto.response.event.ArbitrageEvent;
import com.exchange.scanner.dto.response.event.ArbitrageOpportunity;
import com.exchange.scanner.dto.response.event.EventData;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AppServiceImpl implements AppService {

    private final ExchangeRepository exchangeRepository;

    private final UserMarketSettingsRepository userMarketSettingsRepository;

    private final UserRepository userRepository;

    private final ApiExchangeAdapter apiExchangeAdapter;

    private final OrdersBookRepository ordersBookRepository;

    private final ArbitrageLifecycleRepository arbitrageLifeCycleRepository;

    private final CoinRepository coinRepository;

    private final CoinMarketCapService coinMarketCapService;

    private final ArbitrageService arbitrageService;

    private static final int SCHEDULED_RATE_TIME_FOR_REFRESH_COINS = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_CHAINS = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_TRADING_FEE = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_COIN_VOLUME24H = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_ORDERS_BOOK = 5000;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_COIN_INFO = 1000 * 60 * 60;


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
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_CHAINS, initialDelay = 15000)
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
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_TRADING_FEE, initialDelay = 15000)
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
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_COIN_VOLUME24H, initialDelay = 15000)
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
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_COIN_INFO, initialDelay = 15000)
    public void getCoinMarketCapCoinInfo() {
        long start = System.currentTimeMillis();
        log.info("{} приступил к выполнению задачи getCoinMarketCapCoinInfo", Thread.currentThread().getName());

        CoinMarketCapUtils coinMarketCapUtils = new CoinMarketCapUtils();
        Set<CoinInfoDTO> response = coinMarketCapUtils.getCoinMarketCapCoinInfo(
                exchangeRepository,
                coinMarketCapService,
                userMarketSettingsRepository);

        response.forEach(coinResponse -> {
            List<Coin> coins = coinRepository.findByName(coinResponse.getCoin());
            coins.forEach(coin -> {
                coin.setCoinMarketCapLink(coinResponse.getCoinMarketCapLink() + "/" + coinResponse.getSlug() + "/");
                coin.setLogoLink(coinResponse.getLogoLink());
            });
            coinRepository.saveAll(coins);
        });

        long end = System.currentTimeMillis() - start;
        log.info("Обновление информации о монете от coinmarketcap успешно заврешено. Время выполнения {}s", end / 1000);
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_ORDERS_BOOK, initialDelay = 15000)
    public void getOrderBooks() {
//        long start = System.currentTimeMillis();
//        log.info("{} приступил к выполнению задачи getOrderBooks", Thread.currentThread().getName());

        synchronized (ordersBookRepository) {
            ordersBookRepository.deleteAll();
        }

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
            synchronized (ordersBookRepository) {
                ordersBookRepository.save(ordersBook);
            }
        });


//        long end = System.currentTimeMillis() - start;
//        log.info("Операция обновления стакана цен выполнена. Время выполнения: {}s", end / 1000);
    }

    @Override
    public CompletableFuture<ExchangeData> getExchanges(UserDetails userDetails) {
        return CompletableFuture.supplyAsync(() -> {
                if (userDetails == null) {
                    return new ExchangeData();
                }

                var user = getUser(userDetails);

                UserMarketSettings userMarketSettings = getUserMarketSettings(user);

                return getExchangeData(userMarketSettings);
            }
        );
    }

    @Override
    public CompletableFuture<ExchangeData> updateUserMarketData(UserUpdateMarketData userData, UserDetails userDetails) {
        return CompletableFuture.supplyAsync(() -> {
            if (userDetails == null) {
                return new ExchangeData();
            }

            var user = getUser(userDetails);
            UserMarketSettings userMarketSettings = getUserMarketSettings(user);

            userMarketSettings.setMarketsBuy(new ArrayList<>(userData.getBuyExchanges()));
            userMarketSettings.setMarketsSell(new ArrayList<>(userData.getSellExchanges()));
            userMarketSettings.setCoins(new ArrayList<>(userData.getCoins()));
            userMarketSettings.setProfitSpread(new BigDecimal(userData.getMinProfit()));
            userMarketSettings.setMinVolume(new BigDecimal(userData.getMinDealAmount()));
            userMarketSettings.setMaxVolume(new BigDecimal(userData.getMaxDealAmount()));
            userMarketSettingsRepository.save(userMarketSettings);

            getCoinMarketCapCoinInfo();

            return getExchangeData(userMarketSettings);
        });
    }

    @Override
    public CompletableFuture<Set<ArbitrageEvent>> getArbitrageEvents(UserDetails userDetails) {
        return CompletableFuture.supplyAsync(() -> {
            if (userDetails == null) {
                return new HashSet<>();
            }

            var user = getUser(userDetails);
            UserMarketSettings userMarketSettings = getUserMarketSettings(user);

            ArbitrageUtils arbitrageUtils = new ArbitrageUtils();
            UserTradeEvent userTradeEvent = arbitrageUtils.createUserTradeEvent(
                    exchangeRepository,
                    ordersBookRepository,
                    coinRepository,
                    userMarketSettings
            );

            Set<ArbitrageOpportunity> arbitrageOpportunities = arbitrageService.getArbitrageOpportunities(userTradeEvent);
            Set<ArbitrageEvent> eventSet = createArbitrageEvent(arbitrageOpportunities);

            return updateLifeCycle(eventSet);
        });
    }

    private UserMarketSettings getUserMarketSettings(User user) {
        synchronized (userMarketSettingsRepository) {
            var userMarketSettings = userMarketSettingsRepository.getByUser(user);
            return userMarketSettings.orElseGet(() -> createUserMarketSettingsWithDefaults(user));
        }
    }

    private User getUser(UserDetails userDetails) {
        synchronized (userRepository) {
            return userRepository.findByUsername(userDetails.getUsername()).orElseThrow(() ->
                new UsernameNotFoundException("Пользователь не зарегистрирован")
            );
        }
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

    private ExchangeData getExchangeData(UserMarketSettings userMarketSettings) {
        Set<Exchange> exchanges = new HashSet<>(exchangeRepository.findAll());
        ExchangeData exchangeData = new ExchangeData();
        Set<String> exchangesNames = exchanges.stream().map(Exchange::getName).collect(Collectors.toSet());
        Set<String> coinsNames = coinRepository.findAll().stream()
                .map(Coin::getName)
                .collect(Collectors.toSet());
        Set<String> userMarketsBuy = new HashSet<>(userMarketSettings.getMarketsBuy());
        Set<String> userMarketsSell = new HashSet<>(userMarketSettings.getMarketsSell());
        Set<String> userCoins = new HashSet<>(userMarketSettings.getCoins());
        String minProfit = String.valueOf(userMarketSettings.getProfitSpread());
        String minVolume = String.valueOf(userMarketSettings.getMinVolume());
        String maxVolume = String.valueOf(userMarketSettings.getMaxVolume());
        exchangeData.setExchanges(exchangesNames);
        exchangeData.setCoins(coinsNames);
        exchangeData.setUserMarketsBuy(userMarketsBuy);
        exchangeData.setUserMarketsSell(userMarketsSell);
        exchangeData.setUserCoinsNames(userCoins);
        exchangeData.setMinUserProfit(minProfit);
        exchangeData.setMinUserVolume(minVolume);
        exchangeData.setMaxUserVolume(maxVolume);
        return exchangeData;
    }

    private Set<ArbitrageEvent> updateLifeCycle(Set<ArbitrageEvent> eventSet) {
        Set<ArbitrageEvent> arbitrageEventsWithLifeCycle = new TreeSet<>();

        checkForNotExistedTradeEvents(eventSet);

        eventSet.forEach(event -> {
            ArbitrageEvent arbitrageEvent = new ArbitrageEvent();
            TreeSet<EventData> eventDataList = new TreeSet<>();
            arbitrageEvent.setCoin(event.getCoin());
            arbitrageEvent.setCoinMarketCapLink(event.getCoinMarketCapLink());
            arbitrageEvent.setCoinMarketCapLogo(event.getCoinMarketCapLogo());
            event.getEventData().forEach(data -> {
                Optional<ArbitrageLifecycle> existedEventData = arbitrageLifeCycleRepository.findBySlug(data.getSlug());
                if (existedEventData.isPresent()) {
                    ArbitrageLifecycle existArbitrage = existedEventData.get();
                    long timestamp = existArbitrage.getTimestamp();
                    long currentTimestamp = System.currentTimeMillis();
                    Date lifecycle = new Date(currentTimestamp - timestamp);
                    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("HH:mm:ss");
                    Duration duration = Duration.ofMillis(currentTimestamp - timestamp);
                    System.out.println(duration.minusHours(3));
                    data.setLifeCycle(dateFormat.toString());
                    existArbitrage.setLastUpdate(System.currentTimeMillis());
                } else {
                    data.setLifeCycle("<1m");
                    ArbitrageLifecycle arbitrageLifecycle = new ArbitrageLifecycle();
                    arbitrageLifecycle.setSlug(data.getSlug());
                    arbitrageLifecycle.setTimestamp(System.currentTimeMillis());
                    arbitrageLifecycle.setLastUpdate(System.currentTimeMillis());
                    arbitrageLifeCycleRepository.save(arbitrageLifecycle);
                }
                eventDataList.add(data);
            });
            arbitrageEvent.setEventData(eventDataList);
            arbitrageEventsWithLifeCycle.add(arbitrageEvent);
        });

        return arbitrageEventsWithLifeCycle;
    }

    private void checkForNotExistedTradeEvents(Set<ArbitrageEvent> eventSet) {
        Set<String> existedEventsSlug = arbitrageLifeCycleRepository.findAll().stream()
                .map(ArbitrageLifecycle::getSlug)
                .collect(Collectors.toSet());
        Set<String> newEventsSlug = eventSet.stream()
                .flatMap(event -> event.getEventData().stream())
                .map(EventData::getSlug)
                .collect(Collectors.toSet());
        deleteNotExistsLifeCycleEvents(existedEventsSlug, newEventsSlug);
    }

    private void deleteNotExistsLifeCycleEvents(Set<String> existedEventsSlug, Set<String> newEventsSlug) {
        existedEventsSlug.forEach(slug -> {
            if (!newEventsSlug.contains(slug)) {
                arbitrageLifeCycleRepository.findBySlug(slug)
                        .ifPresent(arbitrageEventLifecycle -> arbitrageLifeCycleRepository
                                .deleteById(arbitrageEventLifecycle.getId()
                        )
                );
            }
        });
    }

    private Set<ArbitrageEvent> createArbitrageEvent(Set<ArbitrageOpportunity> arbitrageOpportunities) {
        Set<ArbitrageEvent> arbitrageEventSet = arbitrageOpportunities.stream().map(arbitrage -> {
            ArbitrageEvent arbitrageEvent = new ArbitrageEvent();
            arbitrageEvent.setCoin(arbitrage.getCoinName());
            arbitrageEvent.setCoinMarketCapLink(arbitrage.getCoinMarketCapLink());
            arbitrageEvent.setCoinMarketCapLogo(arbitrage.getCoinMarketCapLogo());
            arbitrageEvent.setEventData(new TreeSet<>());
            return arbitrageEvent;
        })
        .collect(Collectors.toSet());

        Map<String, List<EventData>> eventDataMap = arbitrageOpportunities.stream()
                .flatMap(opportunity -> opportunity.getTradingData().entrySet().stream()
                        .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue())))
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        for (ArbitrageEvent event : arbitrageEventSet) {
            TreeSet<EventData> eventDataList = new TreeSet<>();
            for (Map.Entry<String, List<EventData>> entry : eventDataMap.entrySet()) {
                if (event.getCoin().equals(entry.getKey())) {
                    eventDataList.addAll(entry.getValue());
                }
            }
            event.setEventData(eventDataList);
        }

        return arbitrageEventSet;
    }
}
