package com.exchange.scanner.services.utils.CoinW;

import com.exchange.scanner.dto.response.exchangedata.coinw.depth.CoinWDepthData;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepthAsk;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepthBid;

import java.util.Set;
import java.util.stream.Collectors;

public class CoinWCoinDepthBuilder {

    public static CoinDepth getCoinDepth(String coinName, CoinWDepthData data) {
        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(coinName);

        Set<CoinDepthAsk> coinDepthAsks = data.getAsks().stream().map(ask -> {
                CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
                coinDepthAsk.setPrice(ask.getFirst());
                coinDepthAsk.setVolume(ask.getLast());
                return coinDepthAsk;
            })
            .collect(Collectors.toSet());

        Set<CoinDepthBid> coinDepthBids = data.getBids().stream().map(bid -> {
                CoinDepthBid coinDepthBid = new CoinDepthBid();
                coinDepthBid.setPrice(bid.getFirst());
                coinDepthBid.setVolume(bid.getLast());
                return coinDepthBid;
            })
            .collect(Collectors.toSet());

        if (coinDepthAsks.isEmpty() || coinDepthBids.isEmpty()) {
            coinDepth.setStatusCode(404);
        } else {
            coinDepth.setStatusCode(200);
        }
        coinDepth.setCoinDepthAsks(coinDepthAsks);
        coinDepth.setCoinDepthBids(coinDepthBids);

        return coinDepth;
    }
}
