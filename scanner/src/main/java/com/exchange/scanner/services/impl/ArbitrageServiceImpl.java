package com.exchange.scanner.services.impl;

import com.exchange.scanner.dto.response.event.*;
import com.exchange.scanner.model.Ask;
import com.exchange.scanner.model.Bid;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.services.ArbitrageService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class ArbitrageServiceImpl implements ArbitrageService {


    @Override
    public Set<ArbitrageOpportunity> getArbitrageOpportunities(UserTradeEvent userTradeEvent) {
        return userTradeEvent.getBuyTradeEventDTO().stream()
                .flatMap(buyEvent -> userTradeEvent.getSellTradeEventDTO().stream()
                        .filter(sellEvent -> !buyEvent.getExchange().equals(sellEvent.getExchange()))
                        .filter(sellEvent -> buyEvent.getCoin().equals(sellEvent.getCoin()))
                        .filter(sellEvent -> isValidArbitrage(buyEvent, sellEvent))
                        .map(sellEvent -> createPossibleOpportunity(buyEvent, sellEvent)))
                .peek(this::printSpreads)
                .map(this::checkForArbitrage)
                .filter(arbitrage -> arbitrage.getTradingData() != null)
                .collect(Collectors.toSet());
    }

    private ArbitrageOpportunity checkForArbitrage(AsksAndBids asksAndBids) {
        BigDecimal maxUserAmount = asksAndBids.buyEvent.getMaxUserTradeAmount();

        return getArbitrageOpportunity(asksAndBids, maxUserAmount);
    }

    private ArbitrageOpportunity getArbitrageOpportunity(AsksAndBids asksAndBids, BigDecimal maxUserAmount) {
        BigDecimal totalTradeVolume = getTotalTradeVolume(asksAndBids);
        BigDecimal withdrawCommission = asksAndBids.buyEvent.getMostProfitableChain().getCommission();
        List<Order> orders = getOrders(asksAndBids, totalTradeVolume);

        List<Order> buyOrders = orders.stream().filter(order -> order != null && order.getType().equals("buy")).toList();
        List<Order> sellOrders = orders.stream().filter(order -> order != null && order.getType().equals("sell")).toList();

        Trade buyTrade = getTrades(maxUserAmount, buyOrders, asksAndBids.buyEvent.getTakerFee(), withdrawCommission);
        Trade sellTrade = getTrades(maxUserAmount, sellOrders, asksAndBids.sellEvent.getTakerFee(), BigDecimal.ZERO);

        return createArbitrageOpportunity(asksAndBids, buyTrade, sellTrade, buyOrders, sellOrders);
    }

    private ArbitrageOpportunity createArbitrageOpportunity(AsksAndBids asksAndBids, Trade buyTrade, Trade sellTrade, List<Order> buyOrders, List<Order> sellOrders) {
        ArbitrageOpportunity arbitrageOpportunity = new ArbitrageOpportunity();
        EventData eventData = new EventData();
        BigDecimal totalAmount = buyTrade.amount.setScale(2, RoundingMode.CEILING);
        BigDecimal totalCoinVolume = buyTrade.totalCoinVolume.setScale(5, RoundingMode.CEILING);
        BigDecimal profit = sellTrade.amount.subtract(buyTrade.amount).setScale(2, RoundingMode.CEILING);
        BigDecimal fee = buyTrade.feeAmount.add(sellTrade.feeAmount).setScale(5, RoundingMode.CEILING);
        BigDecimal withdrawFee = buyTrade.withdrawFee.setScale(5, RoundingMode.CEILING);
        BigDecimal totalFee = fee.add(withdrawFee).setScale(5, RoundingMode.CEILING);
        BigDecimal profitSpread = profit.subtract(totalFee).setScale(5, RoundingMode.CEILING);

        if (profitSpread.compareTo(asksAndBids.buyEvent.getUserMinProfit()) > 0) {
            arbitrageOpportunity.setCoinName(asksAndBids.buyEvent.getCoin());
            arbitrageOpportunity.setCoinMarketCapLogo(asksAndBids.buyEvent.getLogoLink());
            arbitrageOpportunity.setCoinMarketCapLink(asksAndBids.buyEvent.getCoinMarketCapLink());
            eventData.setExchangeForBuy(asksAndBids.buyEvent.getExchange());
            eventData.setExchangeForSell(asksAndBids.sellEvent.getExchange());
            eventData.setDepositLink(asksAndBids.sellEvent.getDepositLink());
            eventData.setWithdrawLink(asksAndBids.buyEvent.getWithdrawLink());
            eventData.setBuyTradingLink(asksAndBids.buyEvent.getTradeLink());
            eventData.setSellTradingLink(asksAndBids.sellEvent.getTradeLink());
            eventData.setFiatVolume(totalAmount.toPlainString());
            eventData.setCoinVolume(totalCoinVolume.toPlainString());
            eventData.setFiatSpread(profitSpread.toPlainString());
            eventData.setAveragePriceForBuy(buyOrders.getFirst().getPrice().toPlainString());
            eventData.setAveragePriceForSell(sellOrders.getFirst().getPrice().toPlainString());
            if (buyOrders.size() > 1) {
                eventData.setPriceRangeForBuy(buyOrders.getFirst().getPrice().toPlainString() + " - " + buyOrders.getLast().getPrice().toPlainString());
            } else {
                eventData.setPriceRangeForBuy(buyOrders.getFirst().getPrice().toPlainString());
            }
            if (sellOrders.size() > 1) {
                eventData.setPriceRangeForSell(sellOrders.getFirst().getPrice().toPlainString() + " - " + sellOrders.getLast().getPrice().toPlainString());
            } else {
                eventData.setPriceRangeForSell(sellOrders.getFirst().getPrice().toPlainString());
            }
            eventData.setVolume24ExchangeForBuy(asksAndBids.buyEvent.getVolume24h().setScale(0, RoundingMode.CEILING).toPlainString());
            eventData.setVolume24ExchangeForSell(asksAndBids.sellEvent.getVolume24h().setScale(0, RoundingMode.CEILING).toPlainString());
            eventData.setOrdersCountForBuy(String.valueOf(buyOrders.size()));
            eventData.setOrdersCountForSell(String.valueOf(sellOrders.size()));
            eventData.setSpotFee(fee.toPlainString());
            eventData.setChainFee(withdrawFee.toPlainString());
            eventData.setChainName(asksAndBids.buyEvent.getMostProfitableChain().getName());
            eventData.setSlug(asksAndBids.buyEvent.getCoin() + "-" + asksAndBids.buyEvent.getExchange() + "-" + asksAndBids.sellEvent.getExchange());
            eventData.setIsWarning(withdrawFee.compareTo(BigDecimal.ZERO) <= 0);
            eventData.setMargin(asksAndBids.sellEvent.getIsMargin());
            Map<String, EventData> tradingData = Collections.singletonMap(arbitrageOpportunity.getCoinName(), eventData);
            arbitrageOpportunity.setTradingData(tradingData);
        }

        return arbitrageOpportunity;
    }

    private static Trade getTrades(BigDecimal maxUserAmount, List<Order> orders, BigDecimal tradeCommission, BigDecimal withdrawCommission) {
        BigDecimal userPriceLimitAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCoinVolume = BigDecimal.ZERO;
        BigDecimal totalCommissionAmount;
        BigDecimal totalWithdrawCommission;

        for (Order order : orders) {
            BigDecimal currentAmount = order.getPrice().multiply(order.getVolume());
            BigDecimal newAmount = userPriceLimitAmount.add(currentAmount);

            if (newAmount.compareTo(maxUserAmount) < 0) {
                userPriceLimitAmount = newAmount;
                totalAmount = totalAmount.add(currentAmount);
                totalCoinVolume = totalCoinVolume.add(order.getVolume());
            } else {
                break;
            }
        }

        totalCommissionAmount = totalAmount.multiply(tradeCommission);
        totalWithdrawCommission = withdrawCommission.multiply(orders.getFirst().getPrice());

        return new Trade(totalAmount, totalCoinVolume, totalCommissionAmount, totalWithdrawCommission);
    }

    private List<Order> getOrders(AsksAndBids asksAndBids, BigDecimal totalTradeVolume) {
        List<Order> orders = new ArrayList<>();
        List<Ask> asks = asksAndBids.buyEvent.getAsks().stream().map(ask -> {
            Ask newAsk = new Ask();
            newAsk.setPrice(ask.getPrice());
            newAsk.setVolume(ask.getVolume());
            return newAsk;
        }).toList();
        List<Bid> bids = asksAndBids.sellEvent.getBids().stream().map(bid -> {
            Bid newBid = new Bid();
            newBid.setPrice(bid.getPrice());
            newBid.setVolume(bid.getVolume());
            return newBid;
        }).toList();

        AtomicReference<BigDecimal> askVolumeRemains = new AtomicReference<>(totalTradeVolume);
        AtomicReference<BigDecimal> bidVolumeRemains = new AtomicReference<>(totalTradeVolume);

        asks.forEach(ask -> {
            BigDecimal tradeVolume = askVolumeRemains.get().compareTo(ask.getVolume()) > 0 ? ask.getVolume() : askVolumeRemains.get();
            askVolumeRemains.getAndUpdate(volume -> volume.subtract(tradeVolume));
            if (tradeVolume.compareTo(BigDecimal.ZERO) > 0) {
                orders.add(createOrder(ask.getPrice(), tradeVolume, "buy"));
            }
        });

        bids.forEach(bid -> {
            BigDecimal tradeVolume = bidVolumeRemains.get().compareTo(bid.getVolume()) > 0 ? bid.getVolume() : bidVolumeRemains.get();
            bidVolumeRemains.getAndUpdate(volume -> volume.subtract(tradeVolume));
            if (tradeVolume.compareTo(BigDecimal.ZERO) > 0) {
                orders.add(createOrder(bid.getPrice(), tradeVolume, "sell"));
            }
        });

        return orders;
    }

    private BigDecimal getTotalTradeVolume(AsksAndBids asksAndBids) {
        List<Ask> asks = asksAndBids.buyEvent.getAsks().stream().map(ask -> {
            Ask newAsk = new Ask();
            newAsk.setPrice(ask.getPrice());
            newAsk.setVolume(ask.getVolume());
            return newAsk;
        }).toList();
        List<Bid> bids = asksAndBids.sellEvent.getBids().stream().map(bid -> {
            Bid newBid = new Bid();
            newBid.setPrice(bid.getPrice());
            newBid.setVolume(bid.getVolume());
            return newBid;
        }).toList();

        int i = 0;
        int j = 0;

        BigDecimal totalAskVolume = BigDecimal.ZERO;
        BigDecimal totalBidVolume = BigDecimal.ZERO;

        while (i < asks.size() && j < bids.size()) {
            Ask ask = asks.get(i);
            Bid bid = bids.get(j);

            if (bid.getPrice().compareTo(ask.getPrice()) >= 0) {
                BigDecimal tradedVolume = ask.getVolume().min(bid.getVolume());

                if (ask.getVolume().compareTo(tradedVolume) > 0) {
                    totalAskVolume = totalAskVolume.add(tradedVolume);
                    ask.setVolume(ask.getVolume().subtract(tradedVolume));
                } else {
                    totalAskVolume = totalAskVolume.add(ask.getVolume());
                    ask.setVolume(ask.getVolume().subtract(ask.getVolume()));
                }

                if (bid.getVolume().compareTo(tradedVolume) > 0) {
                    totalBidVolume = totalBidVolume.add(tradedVolume);
                    bid.setVolume(bid.getVolume().subtract(tradedVolume));
                } else {
                    totalBidVolume = totalBidVolume.add(tradedVolume);
                    bid.setVolume(bid.getVolume().subtract(bid.getVolume()));
                }

                if (ask.getVolume().compareTo(BigDecimal.ZERO) == 0) {
                    i++;
                }

                if (bid.getVolume().compareTo(BigDecimal.ZERO) == 0) {
                    j++;
                }
            } else {
                break;
            }
        }

        return totalAskVolume;
    }

    private boolean isValidArbitrage(UserBuyTradeEventDTO buyEvent, UserSellTradeEventDTO sellEvent) {
        Set<String> askChains = buyEvent.getChains().stream().map(Chain::getName).collect(Collectors.toSet());
        Set<String> bidChains = sellEvent.getChains().stream().map(Chain::getName).collect(Collectors.toSet());
        boolean validChains = bidChains.stream().anyMatch(askChains::contains);

        return !buyEvent.getAsks().isEmpty() && !sellEvent.getBids().isEmpty() &&
                sellEvent.getBids().getFirst().getPrice().compareTo(buyEvent.getAsks().getFirst().getPrice()) > 0 &&
                validChains;
    }

    private AsksAndBids createPossibleOpportunity(UserBuyTradeEventDTO buyEvent, UserSellTradeEventDTO sellEvent) {
        Set<Ask> asks = buyEvent.getAsks().stream()
                .filter(ask -> sellEvent.getBids().stream()
                        .anyMatch(bid -> ask.getPrice().compareTo(bid.getPrice()) < 0))
//                .filter(ask -> !ask.getVolume().equals(new BigDecimal("0.000000")))
                .collect(Collectors.toSet());

        Set<Bid> bids = sellEvent.getBids().stream()
                .filter(bid -> buyEvent.getAsks().stream()
                        .anyMatch(ask -> bid.getPrice().compareTo(ask.getPrice()) > 0))
//                .filter(bid -> !bid.getVolume().equals(new BigDecimal("0.000000")))
                .collect(Collectors.toSet());

        TreeSet<Chain> possibleTransactionChains = getPossibleTransactionChains(buyEvent, sellEvent);

        Chain mostProfitableChain = possibleTransactionChains.first();
        buyEvent.setChains(possibleTransactionChains);
        sellEvent.setChains(possibleTransactionChains);
        buyEvent.setMostProfitableChain(mostProfitableChain);
        sellEvent.setMostProfitableChain(mostProfitableChain);

        buyEvent.setAsks(new TreeSet<>(asks));
        sellEvent.setBids(new TreeSet<>(bids));

        return new AsksAndBids(buyEvent, sellEvent);
    }

    private static TreeSet<Chain> getPossibleTransactionChains(UserBuyTradeEventDTO buyEvent, UserSellTradeEventDTO sellEvent) {
        Set<Chain> askChains = buyEvent.getChains();
        Set<Chain> bidChains = sellEvent.getChains();
        TreeSet<Chain> possibleTransactionChains = new TreeSet<>();
        askChains.forEach(ask -> bidChains.forEach(bid -> {
            if (ask.getName().equalsIgnoreCase(bid.getName())) {
                possibleTransactionChains.add(ask);
            }
        }));
        return possibleTransactionChains;
    }

    private static Order createOrder(BigDecimal price, BigDecimal tradeVolume, String orderType) {
        Order order = new Order();
        order.setPrice(price.setScale(5, RoundingMode.CEILING));
        order.setVolume(tradeVolume);
        order.setType(orderType);
        return order;
    }

    private void printSpreads(AsksAndBids asksAndBids) {
        System.out.println("Биржа покупки: " + asksAndBids.buyEvent().getExchange());
        System.out.println("Монета: " + asksAndBids.buyEvent().getCoin());
        System.out.println("Asks:");
        asksAndBids.buyEvent().getAsks().forEach(ask -> System.out.println(ask.getPrice().toPlainString() + " " + ask.getVolume().toPlainString()));

        System.out.println("Биржа продажи: " + asksAndBids.sellEvent().getExchange());
        System.out.println("Монета: " + asksAndBids.sellEvent().getCoin());
        System.out.println("Bids:");
        asksAndBids.sellEvent().getBids().forEach(bid -> System.out.println(bid.getPrice().toPlainString() + " " + bid.getVolume().toPlainString()));
    }

    private record AsksAndBids(UserBuyTradeEventDTO buyEvent, UserSellTradeEventDTO sellEvent) {}

    private record Trade(BigDecimal amount, BigDecimal totalCoinVolume, BigDecimal feeAmount, BigDecimal withdrawFee){}
}
