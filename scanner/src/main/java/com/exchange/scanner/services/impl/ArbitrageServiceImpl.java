package com.exchange.scanner.services.impl;

import com.exchange.scanner.dto.response.event.*;
import com.exchange.scanner.model.Ask;
import com.exchange.scanner.model.Bid;
import com.exchange.scanner.model.Chain;
import com.exchange.scanner.services.ArbitrageService;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class ArbitrageServiceImpl implements ArbitrageService {


    @Override
    public Set<ArbitrageOpportunity> getArbitrageOpportunities(UserTradeEvent userTradeEvent) {
        userTradeEvent.getBuyTradeEventDTO().stream()
                .flatMap(buyEvent -> userTradeEvent.getSellTradeEventDTO().stream()
                        .filter(sellEvent -> isValidArbitrage(buyEvent, sellEvent))
                        .map(sellEvent -> createPossibleOpportunity(buyEvent, sellEvent)))
                .filter(asksAndBids -> !asksAndBids.buyEvent().getAsks().isEmpty() &&
                        !asksAndBids.sellEvent().getBids().isEmpty())
                .peek(this::printSpreads)
                .map(asksAndBids -> calculateTrade(asksAndBids))
                .collect(Collectors.toSet());

        return Set.of();
    }

    private ArbitrageOpportunity calculateTrade(AsksAndBids asksAndBids) {
        ArbitrageOpportunity arbitrageOpportunity = new ArbitrageOpportunity();
        BigDecimal minUserProfit = asksAndBids.buyEvent.getUserMinProfit();
        BigDecimal minUserAmount = asksAndBids.buyEvent.getMinUserTradeAmount();
        BigDecimal maxUserAmount = asksAndBids.buyEvent.getMaxUserTradeAmount();

        BigDecimal maxTradeProfit = calculateMaxProfit(asksAndBids, maxUserAmount);

        return arbitrageOpportunity;
    }

    private BigDecimal calculateMaxProfit(AsksAndBids asksAndBids, BigDecimal maxUserAmount) {
        List<Order> orders = new ArrayList<>();
        AsksAndBids trade = getTotalTradeVolume(asksAndBids);
        AtomicReference<BigDecimal> totalTradeAskVolume = new AtomicReference<>(trade.buyEvent.getTotalTradeVolume());
        System.out.println("total trade volume: " + totalTradeAskVolume);

        System.out.println(asksAndBids.buyEvent.getExchange());
        System.out.println(asksAndBids.sellEvent.getExchange());
        System.out.println(asksAndBids.buyEvent.getCoin());
        System.out.println(asksAndBids.sellEvent.getCoin());
        long buyOrders = orders.stream().filter(order -> order != null && order.getType().equals("buy")).count();
        long sellOrders = orders.stream().filter(order -> order != null && order.getType().equals("sell")).count();
        System.out.println("buy orders: " + buyOrders);
        System.out.println("sell orders: " + sellOrders);

        return BigDecimal.ZERO;
    }

    private static AsksAndBids getTotalTradeVolume(AsksAndBids asksAndBids) {
        List<Ask> asks = new ArrayList<>(asksAndBids.buyEvent.getAsks());
        List<Bid> bids = new ArrayList<>(asksAndBids.sellEvent.getBids());

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
        asksAndBids.buyEvent.setTotalTradeVolume(totalAskVolume);
        asksAndBids.sellEvent.setTotalTradeVolume(totalBidVolume);
        return asksAndBids;
    }

    private boolean isValidArbitrage(UserBuyTradeEventDTO buyEvent, UserSellTradeEventDTO sellEvent) {
        Set<Chain> askChains = buyEvent.getChains();
        Set<Chain> bidChains = sellEvent.getChains();
        boolean validChains = bidChains.stream().map(askChains::contains).findFirst().orElse(false);

        return !buyEvent.getExchange().equals(sellEvent.getExchange()) &&
                buyEvent.getCoin().equals(sellEvent.getCoin()) &&
                !buyEvent.getAsks().isEmpty() && !sellEvent.getBids().isEmpty() &&
                sellEvent.getBids().getFirst().getPrice().compareTo(buyEvent.getAsks().getFirst().getPrice()) > 0 &&
                validChains;
    }

    private AsksAndBids createPossibleOpportunity(UserBuyTradeEventDTO buyEvent, UserSellTradeEventDTO sellEvent) {
        Set<Ask> asks = buyEvent.getAsks().stream()
                .filter(ask -> sellEvent.getBids().stream()
                        .anyMatch(bid -> ask.getPrice().compareTo(bid.getPrice()) < 0))
                .filter(ask -> !ask.getVolume().equals(new BigDecimal("0.000000")))
                .collect(Collectors.toSet());

        Set<Bid> bids = sellEvent.getBids().stream()
                .filter(bid -> buyEvent.getAsks().stream()
                        .anyMatch(ask -> bid.getPrice().compareTo(ask.getPrice()) > 0))
                .filter(bid -> !bid.getVolume().equals(new BigDecimal("0.000000")))
                .collect(Collectors.toSet());

        Set<Chain> askChains = buyEvent.getChains();
        Set<Chain> bidChains = sellEvent.getChains();
        Set<Chain> possibleTransactionChains = bidChains.stream()
                .filter(bidChain -> askChains.stream().anyMatch(askChain -> askChain.equals(bidChain)))
                .collect(Collectors.toSet());

        Chain mostProfitableChain = new TreeSet<>(possibleTransactionChains).first();
        buyEvent.setAsks(new TreeSet<>(asks));
        sellEvent.setBids(new TreeSet<>(bids));
        buyEvent.setChains(possibleTransactionChains);
        sellEvent.setChains(possibleTransactionChains);
        buyEvent.setMostProfitableChain(mostProfitableChain);
        sellEvent.setMostProfitableChain(mostProfitableChain);

        return new AsksAndBids(buyEvent, sellEvent);
    }

    private static @NotNull Order createOrder(BigDecimal price, BigDecimal tradeVolume, String orderType) {
        Order order = new Order();
        order.setPrice(price);
        order.setVolume(tradeVolume);
        order.setType(orderType);
        return order;
    }

    private void printSpreads(AsksAndBids asksAndBids) {
        System.out.println("Биржа покупки: " + asksAndBids.buyEvent().getExchange());
        System.out.println("Монета: " + asksAndBids.buyEvent().getCoin());
        System.out.println("Asks:");
        asksAndBids.buyEvent().getAsks().forEach(ask -> System.out.println(ask.getPrice() + " " + ask.getVolume()));

        System.out.println("Биржа продажи: " + asksAndBids.sellEvent().getExchange());
        System.out.println("Монета: " + asksAndBids.sellEvent().getCoin());
        System.out.println("Bids:");
        asksAndBids.sellEvent().getBids().forEach(bid -> System.out.println(bid.getPrice() + " " + bid.getVolume()));
    }

    private record AsksAndBids(UserBuyTradeEventDTO buyEvent, UserSellTradeEventDTO sellEvent) {}

}
