package com.exchange.scanner.services.utils.AppUtils;

import com.exchange.scanner.dto.response.event.EventData;
import com.exchange.scanner.model.*;
import com.exchange.scanner.repositories.OrdersBookRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

public class ArbitrageOpportunitiesUtils {

    @Transactional
    public Map<String, List<ArbitrageOpportunity>> checkExchangesForArbitrageOpportunities(
            UserMarketSettings userMarketSettings,
            OrdersBookRepository ordersBookRepository
    )
    {

        Map<String, List<ArbitrageOpportunity>> arbitrageOpportunitiesMap = new HashMap<>();

        userMarketSettings.getCoins().forEach(coinName -> {
            List<ArbitrageOpportunity> arbitrageOpportunities = new ArrayList<>();
            List<OrdersBook> ordersBooks = ordersBookRepository.findByCoinName(coinName);
            Map<String, Set<Ask>> buyPrices = getBuyPrices(ordersBooks);
            Map<String, Set<Bid>> sellPrices = getSellPrices(ordersBooks);
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

    private Map<String, Ask> getLowestBuyPrice(Map<String, Set<Ask>> buyPrices) {
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

    private Map<String, Bid> getHighestSellPrice(Map<String, Set<Bid>> sellPrices) {
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

    private Map<String, Set<Ask>> getBuyPrices(List<OrdersBook> ordersBooks) {
        return ordersBooks.stream()
                .collect(Collectors.toMap(
                        ordersBook -> ordersBook.getExchange().getName(),
                        ordersBook -> new TreeSet<>(ordersBook.getAsks())
                ));
    }

    private Map<String, Set<Bid>> getSellPrices(List<OrdersBook> ordersBooks) {
        return ordersBooks.stream()
                .collect(Collectors.toMap(
                        ordersBook -> ordersBook.getExchange().getName(),
                        ordersBook -> new TreeSet<>(ordersBook.getBids()).reversed()
                ));
    }

    private TradingData getChainData(Bid bid, Ask ask) {
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

    public List<EventData> getEventDataFromArbitrageOpportunities(List<ArbitrageOpportunity> arbitrageOpportunitiesList, UserMarketSettings userMarketSettings) {
        List<EventData> eventDataList = new ArrayList<>();
        BigDecimal userMinProfit = BigDecimal.valueOf(userMarketSettings.getProfitSpread());
        BigDecimal userMaxVolume = BigDecimal.valueOf(userMarketSettings.getMaxVolume());

        arbitrageOpportunitiesList.forEach(arbitrageOpportunity -> {
            EventData eventData = getArbitrageEventData(arbitrageOpportunity, userMaxVolume);
            if (userMinProfit.compareTo(new BigDecimal(eventData.getFiatSpread())) < 0) {
                eventDataList.add(eventData);
            }
        });
        return eventDataList;
    }

    private EventData getArbitrageEventData(ArbitrageOpportunity arbitrageOpportunity, BigDecimal userMaxVolume) {
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

    private BigDecimal calculateTradingFee(Ask ask, Bid bid, BigDecimal tradingFeeAsk, BigDecimal tradingFeeBid) {
        return bid.getPrice().multiply(tradingFeeBid).add(ask.getPrice().multiply(tradingFeeAsk));
    }

    private BigDecimal calculateChainFee(Ask ask, BigDecimal chainFee) {
        return ask.getPrice().multiply(chainFee);
    }

    private BigDecimal calculateProfit(BigDecimal volume, BigDecimal askPrice, BigDecimal bidPrice, BigDecimal fee) {
        return volume.multiply(bidPrice.subtract(askPrice)).subtract(fee);
    }

    private EventData createEventData(
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
