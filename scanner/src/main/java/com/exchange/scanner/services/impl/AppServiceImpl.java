package com.exchange.scanner.services.impl;

import com.exchange.scanner.dto.request.UserUpdateMarketData;
import com.exchange.scanner.dto.response.*;
import com.exchange.scanner.dto.response.event.ArbitrageEventDTO;
import com.exchange.scanner.dto.response.event.EventDataDTO;
import com.exchange.scanner.model.ArbitrageEvent;
import com.exchange.scanner.dto.response.event.ArbitrageOpportunity;
import com.exchange.scanner.model.EventData;
import com.exchange.scanner.dto.response.event.UserTradeEvent;
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
import org.jetbrains.annotations.NotNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
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

    private final AskRepository askRepository;

    private final BidRepository bidRepository;

    private final CoinRepository coinRepository;

    private final ArbitrageEventRepository arbitrageEventRepository;

    private final CoinMarketCapService coinMarketCapService;

    private final ArbitrageService arbitrageService;

    private static final int SCHEDULED_RATE_TIME_FOR_REFRESH_COINS = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_CHAINS = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_TRADING_FEE = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_COIN_VOLUME24H = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_GET_ORDERS_BOOK = 1000 * 60 * 60;

    private static final int SCHEDULED_RATE_TIME_FOR_FINDING_TRADE_EVENTS = 5000;

    private final ReentrantLock lock = new ReentrantLock();

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_REFRESH_COINS, initialDelay = 0)
    public void refreshCoins() {
        try {
            lock.lock();
            long start = System.currentTimeMillis();
            log.debug("{} приступил к выполнению задачи refreshCoins", Thread.currentThread().getName());

            RefreshCoinUtils refreshCoinUtils = new RefreshCoinUtils();
            Map<String, Set<Coin>> coins = refreshCoinUtils.getCoinsAsync(exchangeRepository, apiExchangeAdapter);
            if (!coins.isEmpty()) {
                updateCoins(coins);
            } else {
                log.error("Ошибка обновления списка валют. В базе данных нет списка бирж.");
            }

            long end = System.currentTimeMillis() - start;
            log.debug("Обновление списка валют успешно завершено. Время выполения: {}s", end / 1000);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_CHAINS, initialDelay = 1000)
    public void getCoinsChains() {
        try {
            lock.lock();
            long start = System.currentTimeMillis();
            log.debug("{} приступил к выполнению задачи getCoinsChains", Thread.currentThread().getName());

            CoinChainUtils coinChainUtils = new CoinChainUtils();
            Set<ChainResponseDTO> response = coinChainUtils.getCoinsChainInfoAsync(
                    apiExchangeAdapter,
                    exchangeRepository
            );

            response.forEach(chainResponse -> chainResponse.getCoin().setChains(chainResponse.getChains()));

            long end = System.currentTimeMillis() - start;
            log.debug("Обновление списка сетей успешно завершено. Время выполнения {}s", end / 1000);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_TRADING_FEE, initialDelay = 2000)
    public void getTradingFee() {
        try {
            lock.lock();
            long start = System.currentTimeMillis();
            log.debug("{} приступил к выполнению задачи getTradingFee", Thread.currentThread().getName());

            TradingFeeUtils tradingFeeUtils = new TradingFeeUtils();
            Set<TradingFeeResponseDTO> tradingResponse = tradingFeeUtils.getTradingFeeAsync(
                    apiExchangeAdapter,
                    exchangeRepository
            );

            tradingResponse.forEach(response -> response.getCoin().setTakerFee(response.getTradingFee()));

            long end = System.currentTimeMillis() - start;
            log.debug("Обновление торговых комиссий успешно завершено. Время выполнения {}s", end / 1000);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_COIN_VOLUME24H, initialDelay = 3000)
    public void getVolume24h() {
        try {
            lock.lock();
            long start = System.currentTimeMillis();
            log.debug("{} приступил к выполнению задачи getVolume24h", Thread.currentThread().getName());

            RefreshVolume24hUtils refreshVolume24hUtils = new RefreshVolume24hUtils();
            Set<Volume24HResponseDTO> volume24HResponse = refreshVolume24hUtils.getVolume24hAsync(
                    apiExchangeAdapter,
                    exchangeRepository
            );
            volume24HResponse.forEach(volume24H -> volume24H.getCoin().setVolume24h(volume24H.getVolume24H()));

            long end = System.currentTimeMillis() - start;
            log.debug("Обновление суточного торгового объёма успешно завершено. Время выполнения {}s", end / 1000);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_GET_ORDERS_BOOK, initialDelay = 4000)
    public void getOrderBooks() {
        try {
            lock.lock();
            long start = System.currentTimeMillis();
            log.info("{} приступил к выполнению задачи getOrderBooks", Thread.currentThread().getName());

            OrdersBookUtils ordersBookUtils = new OrdersBookUtils();
            ordersBookUtils.getOrderBooks(exchangeRepository, apiExchangeAdapter);

            long end = System.currentTimeMillis() - start;
            log.info("Операция обновления стакана цен выполнена. Время выполнения: {}s", end / 1000);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_FINDING_TRADE_EVENTS, initialDelay = 5000)
    public void findArbitrageEvents() {
        long start = System.currentTimeMillis();
        log.info("{} приступил к выполнению задачи findArbitrageEvents", Thread.currentThread().getName());

        ArbitrageUtils arbitrageUtils = new ArbitrageUtils();
        UserTradeEvent userTradeEvent = arbitrageUtils.createUserTradeEvent(
                exchangeRepository,
                ordersBookRepository,
                askRepository,
                bidRepository,
                coinRepository,
                lock
        );

        Set<ArbitrageOpportunity> arbitrageOpportunities = arbitrageService.getArbitrageOpportunities(userTradeEvent);
        Set<ArbitrageEvent> eventSet = createArbitrageEvent(arbitrageOpportunities);

        saveArbitrageEvent(eventSet);

        long end = System.currentTimeMillis() - start;
        log.info("Операция нахождения торговых сделок завершена. Время выполнения: {}s", end / 1000);
    }

    @Override
    @Scheduled(fixedRate = SCHEDULED_RATE_TIME_FOR_FINDING_TRADE_EVENTS, initialDelay = Integer.MAX_VALUE)
    public void getCoinMarketCapCoinInfo() {
        long start = System.currentTimeMillis();
        log.info("{} приступил к выполнению задачи getCoinMarketCapCoinInfo", Thread.currentThread().getName());

        Map<String, ArbitrageEvent> arbitrageEventsMap = arbitrageEventRepository.findAll().stream()
                .filter(event -> event.getCoinMarketCapLogo() == null && event.getCoinMarketCapLink() == null)
                .collect(Collectors.toMap(ArbitrageEvent::getCoin, Function.identity()));

        if (arbitrageEventsMap.isEmpty()) return;

        CoinMarketCapUtils coinMarketCapUtils = new CoinMarketCapUtils();
        Set<CoinInfoDTO> response = coinMarketCapUtils.getCoinMarketCapCoinInfo(coinMarketCapService, new HashSet<>(arbitrageEventsMap.keySet()));

        for (CoinInfoDTO coinResponse : response) {
            if (coinResponse.getSlug() != null) {
                ArbitrageEvent arbitrageEvent = arbitrageEventsMap.get(coinResponse.getCoin());
                if (arbitrageEvent != null) {
                    arbitrageEvent.setCoinMarketCapLink(coinResponse.getCoinMarketCapLink() + "/" + coinResponse.getSlug() + "/");
                    arbitrageEvent.setCoinMarketCapLogo(coinResponse.getLogoLink());
                }
            }
        }

        if (!arbitrageEventsMap.isEmpty()) {
            try {
                lock.lock();
                arbitrageEventRepository.saveAll(arbitrageEventsMap.values());
            } finally {
                lock.unlock();
            }
        }

        long end = System.currentTimeMillis() - start;
        log.info("Обновление информации о монете от coinmarketcap успешно завершено. Время выполнения {}s", end / 1000);
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
        });
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

            return getExchangeData(userMarketSettings);
        });
    }

    @Override
    public CompletableFuture<Set<ArbitrageEventDTO>> getArbitrageEvents(UserDetails userDetails) {
        return CompletableFuture.supplyAsync(() -> {
            if (userDetails == null) {
                return new HashSet<>();
            }
            Comparator<ArbitrageEventDTO> comparator = Comparator.comparing(ArbitrageEventDTO::coin);
            Set<ArbitrageEventDTO> arbitrageEvents = new TreeSet<>(comparator);

            var user = getUser(userDetails);
            UserMarketSettings userMarketSettings = getUserMarketSettings(user);
            Set<ArbitrageEvent> arbitrageEventSet = new HashSet<>(arbitrageEventRepository.findAll());
            arbitrageEventSet.stream()
                    .filter(event -> isUserSettingsCoin(event, userMarketSettings))
                    .map(event -> filterEventData(event, userMarketSettings))
                    .forEach(arbitrageEvents::add);

            return arbitrageEvents;
        });
    }

    private boolean isUserSettingsCoin(ArbitrageEvent arbitrageEvent, UserMarketSettings userMarketSettings) {
        if (userMarketSettings.getCoins().isEmpty()) return true;
        Set<String> userCoinsNames = new HashSet<>(userMarketSettings.getCoins());
        return userCoinsNames.contains(arbitrageEvent.getCoin());
    }

    private ArbitrageEventDTO filterEventData(ArbitrageEvent event, UserMarketSettings userMarketSettings) {
        List<EventDataDTO> eventDataDTO = event.getEventData().stream()
            .filter(eventData -> {
                List<String> userMarketsForBuy = userMarketSettings.getMarketsBuy();
                List<String> userMarketsForSell = userMarketSettings.getMarketsSell();
                return userMarketsForBuy.contains(eventData.getExchangeForBuy()) &&
                        userMarketsForSell.contains(eventData.getExchangeForSell());
            })
            .filter(eventData -> {
                BigDecimal currentSpread = BigDecimal.valueOf(Double.parseDouble(eventData.getFiatSpread()));
                BigDecimal userMinSpread = userMarketSettings.getProfitSpread();
                return currentSpread.compareTo(userMinSpread) >= 0;
            })
            .filter(eventData -> {
                BigDecimal currentMinFiatVolume = BigDecimal.valueOf(Double.parseDouble(eventData.getFiatVolume()));
                BigDecimal userMinFiatVolume = userMarketSettings.getMinVolume();
                return currentMinFiatVolume.compareTo(userMinFiatVolume) >= 0;
            })
            .filter(eventData -> {
                BigDecimal currentMaxFiatVolume = BigDecimal.valueOf(Double.parseDouble(eventData.getFiatVolume()));
                BigDecimal userMaxFiatVolume = userMarketSettings.getMaxVolume();
                return currentMaxFiatVolume.compareTo(userMaxFiatVolume) <= 0;
            })
            .map(this::createEventDataDTO)
            .toList();

        return new ArbitrageEventDTO(event.getCoin(), event.getCoinMarketCapLink(), event.getCoinMarketCapLogo(), eventDataDTO);
    }

    private EventDataDTO createEventDataDTO(EventData eventData) {
        String lifeCycle = getLifeCycle(eventData);

        return new EventDataDTO(
            eventData.getExchangeForBuy(),
            eventData.getExchangeForSell(),
            eventData.getDepositLink(),
            eventData.getWithdrawLink(),
            eventData.getBuyTradingLink(),
            eventData.getSellTradingLink(),
            eventData.getFiatVolume(),
            eventData.getCoinVolume(),
            eventData.getFiatSpread(),
            eventData.getAveragePriceForBuy(),
            eventData.getAveragePriceForSell(),
            eventData.getPriceRangeForBuy(),
            eventData.getPriceRangeForSell(),
            eventData.getVolume24ExchangeForBuy(),
            eventData.getVolume24ExchangeForSell(),
            eventData.getOrdersCountForBuy(),
            eventData.getOrdersCountForSell(),
            eventData.getSpotFee(),
            eventData.getChainFee(),
            lifeCycle,
            eventData.getChainName(),
            eventData.getTransactionTime(),
            eventData.getTransactionConfirmation(),
            eventData.getMargin(),
            eventData.getIsWarning());
    }

    @NotNull
    private static String getLifeCycle(EventData eventData) {
        String lifeCycle;
        long timestampMillis = eventData.getTimestamp();
        Instant eventTime = Instant.ofEpochMilli(timestampMillis);
        Instant now = Instant.now();
        Duration duration = Duration.between(eventTime, now);

        long seconds = duration.getSeconds();
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            lifeCycle = String.format("%d дн. %d ч. %d мин. %d сек.", days, hours % 24, minutes % 60, seconds % 60);
        } else if (hours > 0) {
            lifeCycle = String.format("%d ч. %d мин. %d сек.", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            lifeCycle = String.format("%d мин. %d сек.", minutes, seconds % 60);
        } else {
            lifeCycle = String.format("%d сек.", seconds);
        }
        return lifeCycle;
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

    private void saveArbitrageEvent(Set<ArbitrageEvent> arbitrageEvents) {
        Map<Long, ArbitrageEvent> existingEventsById = arbitrageEventRepository.findAll().stream()
                .collect(Collectors.toMap(ArbitrageEvent::getId, Function.identity()));

        Set<ArbitrageEvent> eventsToSave = new HashSet<>();
        Set<Long> eventsToDeleteIds = new HashSet<>(existingEventsById.keySet());


        for (ArbitrageEvent arbitrageEvent : arbitrageEvents) {
            Long arbitrageEventId = arbitrageEvent.getId();
            ArbitrageEvent existingEvent = existingEventsById.get(arbitrageEventId);

            if (existingEvent != null) {
                updateExistingEvent(existingEvent, arbitrageEvent);
                eventsToSave.add(existingEvent);
                eventsToDeleteIds.remove(arbitrageEventId);
            } else {
                eventsToSave.add(arbitrageEvent);
            }
        }

        arbitrageEventRepository.deleteAllById(eventsToDeleteIds);
        arbitrageEventRepository.saveAll(eventsToSave);
    }

    private void updateExistingEvent(ArbitrageEvent existingEvent, ArbitrageEvent newEvent) {
        Set<String> existingSlugs = existingEvent.getEventData().stream().map(EventData::getSlug).collect(Collectors.toSet());
        Set<String> newSlugs = newEvent.getEventData().stream().map(EventData::getSlug).collect(Collectors.toSet());

        Set<EventData> eventDataToRemove = existingEvent.getEventData().stream()
                .filter(data -> !newSlugs.contains(data.getSlug()))
                .collect(Collectors.toSet());

        Set<EventData> eventDataToAdd = newEvent.getEventData().stream()
                .filter(data -> !existingSlugs.contains(data.getSlug()))
                .collect(Collectors.toSet());

        existingEvent.getEventData().removeAll(eventDataToRemove);
        existingEvent.getEventData().addAll(eventDataToAdd);
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

    private Set<ArbitrageEvent> createArbitrageEvent(Set<ArbitrageOpportunity> arbitrageOpportunities) {
        Map<String, Collection<EventData>> allEventData = arbitrageOpportunities.stream()
            .collect(Collectors.toMap(ArbitrageOpportunity::getCoinName,
                opportunity -> opportunity.getTradingData().values(),
                (a, b) -> {
                    List<EventData> merged = new ArrayList<>();
                    merged.addAll(a);
                    merged.addAll(b);
                    return merged;
                }));

        return arbitrageOpportunities.stream().map(opportunity -> {
            ArbitrageEvent arbitrageEvent = new ArbitrageEvent();
            arbitrageEvent.setCoin(opportunity.getCoinName());
            arbitrageEvent.setCoinMarketCapLink(opportunity.getCoinMarketCapLink());
            arbitrageEvent.setCoinMarketCapLogo(opportunity.getCoinMarketCapLogo());
            arbitrageEvent.setEventData(new HashSet<>(allEventData.getOrDefault(opportunity.getCoinName(), Collections.emptyList())));
            return arbitrageEvent;
        }).collect(Collectors.toSet());
    }
}
