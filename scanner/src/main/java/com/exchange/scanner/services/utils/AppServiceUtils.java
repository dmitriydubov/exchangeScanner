package com.exchange.scanner.services.utils;

import com.exchange.scanner.dto.response.event.EventData;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.error.NoExchangesException;
import com.exchange.scanner.model.*;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class AppServiceUtils {

    public static Map<String, Set<Coin>> getCoinsAsync(ExchangeRepository exchangeRepository,
                                                       ApiExchangeAdapter apiExchangeAdapter
    ) {
        List<Exchange> exchanges = exchangeRepository.findAll();
        if (exchanges.isEmpty()) throw new NoExchangesException("Отсутствует список бирж для обновления монет");
        return AppServiceUtils.getExchangeMap(exchanges, apiExchangeAdapter);
    }

    private static Map<String, Set<Coin>> getExchangeMap(List<Exchange> exchanges, ApiExchangeAdapter apiExchangeAdapter) {
        Map<String, Set<Coin>> exchangeMap = new ConcurrentHashMap<>();
        ExecutorService executorService = Executors.newFixedThreadPool(exchanges.size());
        List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();
        exchanges.forEach(exchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<Coin> coins = apiExchangeAdapter.refreshExchangeCoins(exchange);
                synchronized (exchangeMap) {
                    exchangeMap.put(exchange.getName(), coins);
                }
            }, executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();
        return exchangeMap;
    }

    public static Set<Exchange> getUsersExchanges(UserMarketSettingsRepository userMarketSettingsRepository,
                                                  ExchangeRepository exchangeRepository
    ) {
        return userMarketSettingsRepository.findAll().stream()
                .flatMap(settings -> {
                    List<String> marketsBuy = settings.getMarketsBuy();
                    List<String> marketsSell = settings.getMarketsSell();
                    List<String> allMarketsNames = new ArrayList<>();
                    allMarketsNames.addAll(marketsBuy);
                    allMarketsNames.addAll(marketsSell);
                    return allMarketsNames.stream();
                })
                .map(exchangeRepository::findByName)
                .collect(Collectors.toSet());
    }

    public static Set<String> getUsersCoinsNames(UserMarketSettingsRepository userMarketSettingsRepository) {
        return userMarketSettingsRepository.findAll().stream()
                .flatMap(settings -> settings.getCoins().stream())
                .collect(Collectors.toSet());
    }

    public static List<OrdersBook> getOrderBooksAsync(
            ExchangeRepository exchangeRepository,
            ApiExchangeAdapter apiExchangeAdapter,
            UserMarketSettingsRepository userMarketSettingsRepository
    ) {
        List<OrdersBook> ordersBooks = Collections.synchronizedList(new ArrayList<>());
        Set<Exchange> exchanges = AppServiceUtils.getUsersExchanges(userMarketSettingsRepository, exchangeRepository);
        if (exchanges.isEmpty()) return new ArrayList<>();
        Set<String> usersCoinsNames = AppServiceUtils.getUsersCoinsNames(userMarketSettingsRepository);
        ExecutorService executorService = Executors.newFixedThreadPool(exchanges.size());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        exchanges.forEach(exchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<String> filteredCoinsNames = AppServiceUtils.getFilteredCoinsNames(exchange, usersCoinsNames);
                Set<CoinDepth> coinDepth = apiExchangeAdapter.getOrderBook(exchange, filteredCoinsNames);
                coinDepth.forEach(depth -> {
                    OrdersBook ordersBook = AppServiceUtils.createOrderBook(exchange, depth);
                    ordersBooks.add(ordersBook);
                });
            },executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return ordersBooks;
    }

    public synchronized static Set<String> getFilteredCoinsNames(Exchange exchange, Set<String> usersCoinsNames) {

        Set<String> filteredCoinNames = new HashSet<>();
        List<String> exchangesCoinsNames = new ArrayList<>(exchange.getCoins().stream().map(Coin::getSymbol).toList());

        exchangesCoinsNames.forEach(exCoin -> usersCoinsNames.forEach(uCoin -> {
            if (exCoin.equals(uCoin)) {
                filteredCoinNames.add(exCoin);
            }
        }));

        return filteredCoinNames;
    }

    public static OrdersBook createOrderBook(Exchange exchange, CoinDepth depth) {

        OrdersBook ordersBook = new OrdersBook();
        ordersBook.setExchange(exchange);
        ordersBook.setCoin(
                exchange.getCoins().stream()
                        .filter(coin -> coin.getName().equals(depth.getCoinName()))
                        .findFirst().orElseThrow(() -> new RuntimeException("Ошибка в методе getOrderBooks. Монеты нет в списке монет биржи"))
        );

        List<Bid> bids = depth.getCoinDepthBids().stream()
                .map(depthBid -> {
                    Bid bid = new Bid();
                    bid.setOrdersBook(ordersBook);
                    bid.setPrice(new BigDecimal(depthBid.getPrice()));
                    bid.setVolume(new BigDecimal(depthBid.getVolume()));
                    return bid;
                })
                .toList();

        List<Ask> asks = depth.getCoinDepthAsks().stream()
                .map(depthAsk -> {
                    Ask ask = new Ask();
                    ask.setOrdersBook(ordersBook);
                    ask.setPrice(new BigDecimal(depthAsk.getPrice()));
                    ask.setVolume(new BigDecimal(depthAsk.getVolume()));
                    return ask;
                })
                .toList();

        ordersBook.setBids(bids);
        ordersBook.setAsks(asks);

        return ordersBook;
    }

    public static Map<String, List<ArbitrageOpportunity>> checkExchangesForArbitrageOpportunities(
            UserMarketSettings userMarketSettings,
            OrdersBookRepository ordersBookRepository
    ) {

        Map<String, List<ArbitrageOpportunity>> arbitrageOpportunitiesMap = new HashMap<>();

        userMarketSettings.getCoins().forEach(coinName -> {
            List<ArbitrageOpportunity> arbitrageOpportunities = new ArrayList<>();
            List<OrdersBook> ordersBooks = ordersBookRepository.findByCoinName(coinName);
            Map<String, Set<Ask>> buyPrices = AppServiceUtils.getBuyPrices(ordersBooks);
            Map<String, Set<Bid>> sellPrices = AppServiceUtils.getSellPrices(ordersBooks);
            Map<String, Ask> lowestBuyPrice = buyPrices.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> {
                                Set<Ask> askSet = entry.getValue();
                                return !askSet.isEmpty() ?
                                        new ArrayList<>(askSet).getFirst() :
                                        new Ask();
                            }
                    ));
            Map<String, Bid> highestSellPrice = sellPrices.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> {
                                Set<Bid> askSet = entry.getValue();
                                return !askSet.isEmpty() ?
                                        new ArrayList<>(askSet).getFirst() :
                                        new Bid();
                            }
                    ));

            lowestBuyPrice.forEach((exchangeForBuy, ask) -> highestSellPrice.forEach((exchangeForSell, bid) -> {
                if (!exchangeForBuy.equals(exchangeForSell) && ask.getPrice() != null && bid.getPrice() != null) {
                    BigDecimal spread = bid.getPrice().subtract(ask.getPrice());

                    if (spread.compareTo(new BigDecimal(0)) > 0) {
                        ArbitrageOpportunity arbitrageOpportunity = ArbitrageOpportunity.builder()
                                .coinName(coinName)
                                .exchangeForBuy(exchangeForBuy)
                                .exchangeForSell(exchangeForSell)
                                .exchangeForBuyAsks(buyPrices.get(exchangeForBuy))
                                .exchangeForSellBids(sellPrices.get(exchangeForSell))
                                .averagePriceForBuy(ask.getPrice().toString())
                                .averagePriceForSell(bid.getPrice().toString())
                                .build();
                        arbitrageOpportunities.add(arbitrageOpportunity);
                    }
                }
            }));
            arbitrageOpportunitiesMap.put(coinName, arbitrageOpportunities);
        });
        return arbitrageOpportunitiesMap;
    }

    private static Map<String, Set<Ask>> getBuyPrices(List<OrdersBook> ordersBooks) {
        return ordersBooks.stream()
                .collect(Collectors.toMap(
                        ordersBook -> ordersBook.getExchange().getName(),
                        ordersBook -> new TreeSet<>(ordersBook.getAsks())
                ));
    }

    private static Map<String, Set<Bid>> getSellPrices(List<OrdersBook> ordersBooks) {
        return ordersBooks.stream()
                .collect(Collectors.toMap(
                        ordersBook -> ordersBook.getExchange().getName(),
                        ordersBook -> new TreeSet<>(ordersBook.getBids()).reversed()
                ));
    }

    public static List<EventData> getEventDataFromArbitrageOpportunities(List<ArbitrageOpportunity> arbitrageOpportunitiesList, UserMarketSettings userMarketSettings) {
        List<EventData> eventDataList = new ArrayList<>();

        arbitrageOpportunitiesList.forEach(arbitrageOpportunity -> {
            EventData eventData = AppServiceUtils.getArbitrageEventData(arbitrageOpportunity);
            eventDataList.add(eventData);
        });
        return eventDataList;
    }

    private static EventData getArbitrageEventData(ArbitrageOpportunity arbitrageOpportunity) {

        BigDecimal maxProfit = new BigDecimal(0);
        BigDecimal maxProfitPercentage = new BigDecimal(0);
        BigDecimal priceAmount = new BigDecimal(0);
        BigDecimal coinAmount = new BigDecimal(0);

        int buyOrdersCount = 1;
        int sellOrdersCount = 1;

        BigDecimal maxAskPriceRange = new BigDecimal(0);
        BigDecimal minAskPriceRange = new BigDecimal(Double.MAX_VALUE);
        BigDecimal maxBidPriceRange = new BigDecimal(0);
        BigDecimal minBidPriceRange = new BigDecimal(Double.MAX_VALUE);

//        if (arbitrageOpportunity.getCoinName().equals("MICHI")) {
//            System.out.println("buy");
//            System.out.println(arbitrageOpportunity.getExchangeForBuy());
//            arbitrageOpportunity.getExchangeForBuyAsks().forEach(a -> System.out.println(a.getPrice() + " " + a.getVolume()));
//            System.out.println("sell");
//            System.out.println(arbitrageOpportunity.getExchangeForSell());
//            arbitrageOpportunity.getExchangeForSellBids().forEach(b -> System.out.println(b.getPrice() + " " + b.getVolume()));
//        }

        BigDecimal bidVolumeRemains = new BigDecimal(0);

        for (Ask ask : arbitrageOpportunity.getExchangeForBuyAsks()) {
            BigDecimal askVolumeRemains = ask.getVolume();
            for (Bid bid : arbitrageOpportunity.getExchangeForSellBids()) {
                bidVolumeRemains = bidVolumeRemains.compareTo(BigDecimal.valueOf(0)) > 0 ?
                        bidVolumeRemains :
                        bid.getVolume();
                if (ask.getPrice().compareTo(bid.getPrice()) < 0) {
                    BigDecimal volume = askVolumeRemains.min(bidVolumeRemains);

                    if (volume.compareTo(new BigDecimal(0)) > 0) {
                        BigDecimal profit = (bid.getPrice().subtract(ask.getPrice())).multiply(volume);
                        maxProfitPercentage = maxProfit.add(calculateProfitPercentage(ask.getPrice(), bid.getPrice(), volume.multiply(ask.getPrice())));
                        maxProfit = maxProfit.add(profit);
                        priceAmount = priceAmount.add(ask.getPrice().multiply(volume));
                        coinAmount = coinAmount.add(volume);

                        askVolumeRemains = askVolumeRemains.subtract(volume);
                        bidVolumeRemains = bidVolumeRemains.subtract(volume);

                        maxAskPriceRange = maxAskPriceRange.max(ask.getPrice());
                        minAskPriceRange = minAskPriceRange.min(ask.getPrice());
                        maxBidPriceRange = maxBidPriceRange.max(bid.getPrice());
                        minBidPriceRange = minBidPriceRange.min(bid.getPrice());

                        if (bidVolumeRemains.compareTo(new BigDecimal(0)) <= 0) {
                            sellOrdersCount++;
                        }

                        if (askVolumeRemains.compareTo(new BigDecimal(0)) <= 0) {
                            buyOrdersCount++;
                            break;
                        }
                    }
                }
            }
        }

        EventData eventData = new EventData();
        eventData.setExchangeForBuy(arbitrageOpportunity.getExchangeForBuy());
        eventData.setExchangeForSell(arbitrageOpportunity.getExchangeForSell());
        eventData.setFiatVolume(priceAmount.setScale(2, RoundingMode.CEILING).toString());
        eventData.setCoinVolume(coinAmount.setScale(2, RoundingMode.CEILING).toString());
        eventData.setFiatSpread(maxProfit.setScale(4,RoundingMode.CEILING).toString());
        eventData.setPercentSpread(maxProfitPercentage.setScale(4, RoundingMode.CEILING) + "%");
        eventData.setAveragePriceForBuy(arbitrageOpportunity.getAveragePriceForBuy());
        eventData.setAveragePriceForSell(arbitrageOpportunity.getAveragePriceForSell());
        if (buyOrdersCount > 1) {
            eventData.setPriceRangeForBuy(minAskPriceRange + "-" + maxAskPriceRange);
        } else {
            eventData.setPriceRangeForBuy(minAskPriceRange.toString());
        }
        if (sellOrdersCount > 1) {
            eventData.setPriceRangeForSell(maxBidPriceRange + "-" + minBidPriceRange);
        } else {
            eventData.setPriceRangeForSell(maxAskPriceRange.toString());
        }
        eventData.setOrdersCountForBuy(String.valueOf(buyOrdersCount));
        eventData.setOrdersCountForSell(String.valueOf(sellOrdersCount));

        return eventData;
    }

    private static BigDecimal calculateProfitPercentage(BigDecimal priceAsk, BigDecimal priceBid, BigDecimal volume) {
        return volume.multiply(priceBid.subtract(priceAsk));
    }
}
