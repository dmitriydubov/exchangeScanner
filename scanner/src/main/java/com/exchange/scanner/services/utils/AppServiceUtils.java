package com.exchange.scanner.services.utils;

import com.exchange.scanner.dto.response.event.EventData;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.*;
import com.exchange.scanner.repositories.ExchangeRepository;
import com.exchange.scanner.repositories.OrdersBookRepository;
import com.exchange.scanner.repositories.UserMarketSettingsRepository;
import com.exchange.scanner.services.ApiExchangeAdapter;
import org.springframework.transaction.annotation.Transactional;

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
        if (exchanges.isEmpty()) return new HashMap<>();
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

    public static Map<String, Set<Coin>> getCoinsChainInfoAsync(ApiExchangeAdapter apiExchangeAdapter,
                                                                ExchangeRepository exchangeRepository,
                                                                UserMarketSettingsRepository userMarketSettingsRepository
    ) {
        Map<String, Set<Coin>> result = new HashMap<>();
        Set<Exchange> userExchanges = getUsersExchanges(userMarketSettingsRepository, exchangeRepository);
        Set<String> usersCoinsNames = getUsersCoinsNames(userMarketSettingsRepository);

        ExecutorService executorService = Executors.newFixedThreadPool(userExchanges.size());
        List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();


        userExchanges.forEach(exchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<Coin> filteredCoinsNames = getFilteredCoins(exchange, usersCoinsNames);
                synchronized (result) {
                    result.putAll(apiExchangeAdapter.getCoinChain(exchange.getName(), filteredCoinsNames));
                }
            }, executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();

        return result;
    }

    public static Map<String, Set<Coin>> getTradingFeeAsync(ApiExchangeAdapter apiExchangeAdapter,
                                                            ExchangeRepository exchangeRepository,
                                                            UserMarketSettingsRepository userMarketSettingsRepository
    ) {
        Map<String, Set<Coin>> result = new HashMap<>();
        Set<Exchange> userExchanges = getUsersExchanges(userMarketSettingsRepository, exchangeRepository);
        Set<String> usersCoinsNames = getUsersCoinsNames(userMarketSettingsRepository);
        ExecutorService executorService = Executors.newFixedThreadPool(userExchanges.size());
        List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();

        userExchanges.forEach(exchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<Coin> filteredCoinsNames = getFilteredCoins(exchange, usersCoinsNames);
                synchronized (result) {
                    result.putAll(apiExchangeAdapter.getTradingFee(exchange.getName(), filteredCoinsNames));
                }
            }, executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();

        return result;
    }

    public static Map<String, Set<Coin>> getVolume24hAsync(ApiExchangeAdapter apiExchangeAdapter,
                                                           ExchangeRepository exchangeRepository,
                                                           UserMarketSettingsRepository userMarketSettingsRepository)
    {
        Map<String, Set<Coin>> result = new HashMap<>();
        Set<Exchange> userExchanges = getUsersExchanges(userMarketSettingsRepository, exchangeRepository);
        Set<String> usersCoinsNames = getUsersCoinsNames(userMarketSettingsRepository);
        ExecutorService executorService = Executors.newFixedThreadPool(userExchanges.size());
        List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();

        userExchanges.forEach(exchange -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Set<Coin> filteredCoinsNames = getFilteredCoins(exchange, usersCoinsNames);
                synchronized (result) {
                    result.putAll(apiExchangeAdapter.getCoinVolume24h(exchange.getName(), filteredCoinsNames));
                }
            }, executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();

        return result;
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

    public static synchronized Set<Coin> getFilteredCoins(Exchange exchange, Set<String> usersCoinsNames) {
        return exchange.getCoins().stream()
                .filter(coin -> usersCoinsNames.contains(coin.getName()))
                .collect(Collectors.toSet());
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

    @Transactional
    public static Map<String, List<ArbitrageOpportunity>> checkExchangesForArbitrageOpportunities(
            UserMarketSettings userMarketSettings,
            OrdersBookRepository ordersBookRepository
    )
    {

        Map<String, List<ArbitrageOpportunity>> arbitrageOpportunitiesMap = new HashMap<>();

        userMarketSettings.getCoins().forEach(coinName -> {
            List<ArbitrageOpportunity> arbitrageOpportunities = new ArrayList<>();
            List<OrdersBook> ordersBooks = ordersBookRepository.findByCoinName(coinName);
            Map<String, Set<Ask>> buyPrices = AppServiceUtils.getBuyPrices(ordersBooks);
            Map<String, Set<Bid>> sellPrices = AppServiceUtils.getSellPrices(ordersBooks);
            Map<String, Ask> lowestBuyPrice = getLowestBuyPrice(buyPrices);
            Map<String, Bid> highestSellPrice = getHighestSellPrice(sellPrices);

            lowestBuyPrice.forEach((exchangeForBuy, ask) -> highestSellPrice.forEach((exchangeForSell, bid) -> {
                if (!exchangeForBuy.equals(exchangeForSell) && ask.getPrice() != null && bid.getPrice() != null) {
                    BigDecimal spread = bid.getPrice().subtract(ask.getPrice());
                    TradingData tradingData = getChainData(bid, ask);
                    if (spread.compareTo(new BigDecimal(0)) > 0 && !tradingData.getChainName().isEmpty()) {
                        ArbitrageOpportunity arbitrageOpportunity = ArbitrageOpportunity.builder()
                                .coinName(coinName)
                                .exchangeForBuy(exchangeForBuy)
                                .exchangeForSell(exchangeForSell)
                                .exchangeForBuyAsks(buyPrices.get(exchangeForBuy))
                                .exchangeForSellBids(sellPrices.get(exchangeForSell))
                                .averagePriceForBuy(ask.getPrice().toString())
                                .averagePriceForSell(bid.getPrice().toString())
                                .tradingData(tradingData)
                                .build();
                        arbitrageOpportunities.add(arbitrageOpportunity);
                    }
                }
            }));
            arbitrageOpportunitiesMap.put(coinName, arbitrageOpportunities);
        });
        return arbitrageOpportunitiesMap;
    }

    private static Map<String, Ask> getLowestBuyPrice(Map<String, Set<Ask>> buyPrices) {
        return buyPrices.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            Set<Ask> askSet = entry.getValue();
                            return !askSet.isEmpty() ?
                                    new ArrayList<>(askSet).getFirst() :
                                    new Ask();
                        }
                ));
    }

    private static Map<String, Bid> getHighestSellPrice(Map<String, Set<Bid>> sellPrices) {
        return sellPrices.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            Set<Bid> askSet = entry.getValue();
                            return !askSet.isEmpty() ?
                                    new ArrayList<>(askSet).getFirst() :
                                    new Bid();
                        }
                ));
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

    @Transactional
    private static TradingData getChainData(Bid bid, Ask ask) {
        Coin coinAsk = ask.getOrdersBook().getCoin();
        Coin coinBid = bid.getOrdersBook().getCoin();
        Set<Chain> chainsForBuy = coinAsk.getChains();
        Set<Chain> chainsForSell = coinBid.getChains();
        String chainName = "";
        BigDecimal minFeeForBuyExchange = new BigDecimal("100");

        for (Chain chainBid : chainsForSell) {
            for (Chain chainAsk : chainsForBuy) {
                if (chainBid.getName().equalsIgnoreCase(chainAsk.getName())) {
                    BigDecimal commission = chainAsk.getCommission();
                    if (commission.compareTo(BigDecimal.ZERO) == 0) {
                        commission = chainBid.getCommission();
                    }
                    chainName = chainBid.getName();
                    minFeeForBuyExchange = minFeeForBuyExchange.min(commission);
                }
            }
        }

        return TradingData.builder()
                .volume24hAsk(coinAsk.getVolume24h())
                .volume24hBid(coinBid.getVolume24h())
                .tradingFeeAsk(coinAsk.getTakerFee())
                .tradingFeeBid(coinBid.getTakerFee())
                .chainName(chainName)
                .chainFeeAmount(minFeeForBuyExchange)
                .build();
    }

    public static List<EventData> getEventDataFromArbitrageOpportunities(List<ArbitrageOpportunity> arbitrageOpportunitiesList, UserMarketSettings userMarketSettings) {
        List<EventData> eventDataList = new ArrayList<>();
        BigDecimal userMinProfit = BigDecimal.valueOf(userMarketSettings.getProfitSpread());
        BigDecimal userMaxVolume = BigDecimal.valueOf(userMarketSettings.getMaxVolume());

        arbitrageOpportunitiesList.forEach(arbitrageOpportunity -> {
            EventData eventData = AppServiceUtils.getArbitrageEventData(arbitrageOpportunity, userMaxVolume);
            if (userMinProfit.compareTo(new BigDecimal(eventData.getFiatSpread())) < 0) {
                eventDataList.add(eventData);
            }
        });
        return eventDataList;
    }

    private static EventData getArbitrageEventData(ArbitrageOpportunity arbitrageOpportunity, BigDecimal userMaxVolume) {
        BigDecimal maxProfit = BigDecimal.ZERO;
        BigDecimal maxProfitCoin = BigDecimal.ZERO;
        BigDecimal priceAmount = BigDecimal.ZERO;
        BigDecimal coinAmount = BigDecimal.ZERO;

        BigDecimal chainFee = arbitrageOpportunity.getTradingData().getChainFeeAmount();
        BigDecimal tradingFeeAsk = arbitrageOpportunity.getTradingData().getTradingFeeAsk();
        BigDecimal tradingFeeBid = arbitrageOpportunity.getTradingData().getTradingFeeBid();
        BigDecimal feeAmount = BigDecimal.ZERO;
        BigDecimal feeChainAmount = BigDecimal.ZERO;

        int buyOrdersCount = 1;
        int sellOrdersCount = 1;

        BigDecimal maxAskPriceRange = BigDecimal.ZERO;
        BigDecimal minAskPriceRange = new BigDecimal(Double.toString(Double.MAX_VALUE));
        BigDecimal maxBidPriceRange = BigDecimal.ZERO;
        BigDecimal minBidPriceRange = new BigDecimal(Double.toString(Double.MAX_VALUE));
        BigDecimal bidVolumeRemains = BigDecimal.ZERO;

        for (Ask ask : arbitrageOpportunity.getExchangeForBuyAsks()) {
            BigDecimal askVolumeRemains = ask.getVolume();

            for (Bid bid : arbitrageOpportunity.getExchangeForSellBids()) {
                bidVolumeRemains = bidVolumeRemains.compareTo(BigDecimal.ZERO) > 0 ? bidVolumeRemains : bid.getVolume();

                if (ask.getPrice().compareTo(bid.getPrice()) < 0) {
                    BigDecimal volume = askVolumeRemains.min(bidVolumeRemains);

                    if (volume.compareTo(BigDecimal.ZERO) > 0) {
                        System.out.println("coin: " + arbitrageOpportunity.getCoinName());
                        BigDecimal currentTradingFee = calculateTradingFee(ask, bid, tradingFeeAsk, tradingFeeBid);
                        System.out.println("current trading fee: " + currentTradingFee);
                        BigDecimal currentChainFee = calculateChainFee(ask, chainFee);
                        System.out.println("current chain fee: " + currentChainFee);
                        BigDecimal profit = calculateProfit(volume, ask.getPrice(), bid.getPrice(), currentTradingFee.add(currentChainFee));
                        System.out.println("current profit: " + profit);

                        feeAmount = feeAmount.add(currentTradingFee);
                        System.out.println("fee amount: " + feeAmount);
                        feeChainAmount = feeChainAmount.add(currentChainFee);
                        System.out.println("fee chain amount: " + feeChainAmount);
                        maxProfitCoin = maxProfitCoin.add(profit.divide(bid.getPrice(), RoundingMode.CEILING));
                        System.out.println("max profit coin: " + maxProfitCoin);

                        if (priceAmount.add(ask.getPrice().multiply(volume)).compareTo(userMaxVolume) > 0) {
                            return createEventData(
                                    arbitrageOpportunity,
                                    priceAmount,
                                    coinAmount,
                                    maxProfit,
                                    maxProfitCoin,
                                    buyOrdersCount,
                                    minAskPriceRange,
                                    maxAskPriceRange,
                                    sellOrdersCount,
                                    maxBidPriceRange,
                                    minBidPriceRange,
                                    feeAmount,
                                    feeChainAmount
                            );
                        }

                        priceAmount = priceAmount.add(ask.getPrice().multiply(volume));
                        System.out.println("price amount: " + priceAmount);
                        maxProfit = maxProfit.add(profit);
                        System.out.println("max profit: " + maxProfit);
                        coinAmount = coinAmount.add(volume);
                        System.out.println("coin amount: " + coinAmount);

                        askVolumeRemains = askVolumeRemains.subtract(volume);
                        bidVolumeRemains = bidVolumeRemains.subtract(volume);

                        maxAskPriceRange = maxAskPriceRange.max(ask.getPrice());
                        minAskPriceRange = minAskPriceRange.min(ask.getPrice());
                        maxBidPriceRange = maxBidPriceRange.max(bid.getPrice());
                        minBidPriceRange = minBidPriceRange.min(bid.getPrice());

                        if (bidVolumeRemains.compareTo(BigDecimal.ZERO) <= 0) {
                            sellOrdersCount++;
                        }
                        if (askVolumeRemains.compareTo(BigDecimal.ZERO) <= 0) {
                            buyOrdersCount++;
                            break;
                        }
                    }
                }
            }
        }

        return createEventData(
                arbitrageOpportunity,
                priceAmount,
                coinAmount,
                maxProfit,
                maxProfitCoin,
                buyOrdersCount,
                minAskPriceRange,
                maxAskPriceRange,
                sellOrdersCount,
                maxBidPriceRange,
                minBidPriceRange,
                feeAmount,
                feeChainAmount
        );
    }

    private static BigDecimal calculateTradingFee(Ask ask, Bid bid, BigDecimal tradingFeeAsk, BigDecimal tradingFeeBid) {
        return bid.getPrice().multiply(tradingFeeBid).add(ask.getPrice().multiply(tradingFeeAsk));
    }

    private static BigDecimal calculateChainFee(Ask ask, BigDecimal chainFee) {
        return ask.getPrice().multiply(chainFee);
    }

    private static BigDecimal calculateProfit(BigDecimal volume, BigDecimal askPrice, BigDecimal bidPrice, BigDecimal fee) {
        return volume.multiply(bidPrice.subtract(askPrice)).subtract(fee);
    }

    private static EventData createEventData(
            ArbitrageOpportunity arbitrageOpportunity,
            BigDecimal priceAmount,
            BigDecimal coinAmount,
            BigDecimal maxProfit,
            BigDecimal maxProfitCoin,
            int buyOrdersCount,
            BigDecimal minAskPriceRange,
            BigDecimal maxAskPriceRange,
            int sellOrdersCount,
            BigDecimal maxBidPriceRange,
            BigDecimal minBidPriceRange,
            BigDecimal feeAmount,
            BigDecimal feeChainAmount
    )
    {
        EventData eventData = new EventData();
        eventData.setExchangeForBuy(arbitrageOpportunity.getExchangeForBuy());
        eventData.setExchangeForSell(arbitrageOpportunity.getExchangeForSell());
        eventData.setFiatVolume(priceAmount.setScale(2, RoundingMode.CEILING).toString());
        eventData.setCoinVolume(coinAmount.setScale(2, RoundingMode.CEILING).toString());
        eventData.setFiatSpread(maxProfit.setScale(2,RoundingMode.CEILING).toString());
        eventData.setCoinSpread(maxProfitCoin.setScale(4, RoundingMode.CEILING) + "%");
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
        eventData.setVolume24ExchangeForBuy(String.valueOf(arbitrageOpportunity.getTradingData().getVolume24hAsk()));
        eventData.setVolume24ExchangeForSell(String.valueOf(arbitrageOpportunity.getTradingData().getVolume24hBid()));
        eventData.setSpotFee(String.valueOf(feeAmount.setScale(2, RoundingMode.CEILING)));
        eventData.setChainFee(String.valueOf(feeChainAmount.setScale(2, RoundingMode.CEILING)));
        eventData.setChainName(arbitrageOpportunity.getTradingData().getChainName());
        return eventData;
    }
}
