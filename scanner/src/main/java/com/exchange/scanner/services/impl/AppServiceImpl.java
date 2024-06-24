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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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
    
    private static final int SCHEDULED_RATE_TIME_FOR_GET_ORDERS = 5000;

    @Override
    @Transactional
    public Set<String> getExchanges() {
        return exchangeRepository.findAll().stream().map(Exchange::getName).collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public List<ArbitrageEvent> getArbitrageOpportunities(UserDetails userDetails) {
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
        Map<String, List<ArbitrageOpportunity>> arbitrageOpportunities = AppServiceUtils
                .checkExchangesForArbitrageOpportunities(userMarketSettings, ordersBookRepository);

        arbitrageOpportunities.forEach((coinName, arbitrageOpportunitiesList) -> {

            ArbitrageEvent arbitrageEvent = new ArbitrageEvent();
            List<EventData> eventDataList = AppServiceUtils.getEventDataFromArbitrageOpportunities(arbitrageOpportunitiesList, userMarketSettings);
            arbitrageEvent.setEventData(eventDataList);

            if (!arbitrageEvent.getEventData().isEmpty()) {
                arbitrageEvent.setCoin(coinName);
                arbitrageEvents.add(arbitrageEvent);
            }
        });


        return arbitrageEvents;
    }

    @Override
//    @Scheduled(fixedRate = 1000 * 60)
    public void refreshCoins() {
        Map<String, Set<Coin>> updatedCoinsMap = AppServiceUtils.getCoinsAsync(exchangeRepository, apiExchangeAdapter);
        updateCoins(updatedCoinsMap);

        log.info("Обновление списка валют успешно завершено");
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
//        log.info("Обновление стаканов цен успешно завершено");
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
