package com.exchange.scanner.services.utils.Bybit;


import com.exchange.scanner.dto.response.exchangedata.bybit.depth.BybitDepthResult;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepthAsk;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepthBid;

import java.util.Set;
import java.util.stream.Collectors;

public class BybitCoinDepthBuilder {

    public static CoinDepth getCoinDepth(String coinName, BybitDepthResult result) {
        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(coinName);

        Set<CoinDepthAsk> coinDepthAskSet = result.getAsks().stream()
                .map(ask -> {
                    CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
                    coinDepthAsk.setPrice(ask.getFirst());
                    coinDepthAsk.setVolume(ask.getLast());
                    return coinDepthAsk;
                })
                .collect(Collectors.toSet());

        Set<CoinDepthBid> coinDepthBidSet = result.getBids().stream()
                .map(bid -> {
                    CoinDepthBid coinDepthBid = new CoinDepthBid();
                    coinDepthBid.setPrice(bid.getFirst());
                    coinDepthBid.setVolume(bid.getLast());
                    return coinDepthBid;
                })
                .collect(Collectors.toSet());

        if (coinDepthAskSet.isEmpty() || coinDepthBidSet.isEmpty()) {
            coinDepth.setStatusCode(404);
        } else {
            coinDepth.setStatusCode(200);
        }

        coinDepth.setCoinDepthAsks(coinDepthAskSet);
        coinDepth.setCoinDepthBids(coinDepthBidSet);

        return coinDepth;
    }
}