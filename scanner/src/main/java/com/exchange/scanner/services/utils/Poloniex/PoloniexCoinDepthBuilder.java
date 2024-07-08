package com.exchange.scanner.services.utils.Poloniex;

import com.exchange.scanner.dto.response.exchangedata.poloniex.depth.PoloniexCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepthAsk;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepthBid;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class PoloniexCoinDepthBuilder {

    public static CoinDepth getPoloniexCoinDepth(PoloniexCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = new HashSet<>();

        int checkBidOrdersSum = 0;
        for (int i = 0; i < depth.getBids().size(); i+=2) {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(new BigDecimal(depth.getBids().get(i)));
            coinDepthBid.setVolume(new BigDecimal(depth.getBids().get(i + 1)));
            checkBidOrdersSum += 2;
            coinDepthBids.add(coinDepthBid);
            if (checkBidOrdersSum == depth.getBids().size()) break;
        }

        Set<CoinDepthAsk> coinDepthAsks =new HashSet<>();

        int checkAskOrdersSum = 0;
        for (int i = 0; i < depth.getAsks().size(); i+=2) {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(new BigDecimal(depth.getAsks().get(i)));
            coinDepthAsk.setVolume(new BigDecimal(depth.getAsks().get(i + 1)));
            checkAskOrdersSum += 2;
            coinDepthAsks.add(coinDepthAsk);
            if (checkAskOrdersSum == depth.getAsks().size()) break;
        }

        if (coinDepthAsks.isEmpty() || coinDepthBids.isEmpty()) {
            coinDepth.setStatusCode(404);
        } else {
            coinDepth.setStatusCode(200);
        }
        coinDepth.setCoinDepthBids(new TreeSet<>(coinDepthBids));
        coinDepth.setCoinDepthAsks(new TreeSet<>(coinDepthAsks));

        return coinDepth;
    }
}
