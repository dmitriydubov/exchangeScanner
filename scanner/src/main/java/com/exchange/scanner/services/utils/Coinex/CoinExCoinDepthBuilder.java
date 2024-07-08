package com.exchange.scanner.services.utils.Coinex;

import com.exchange.scanner.dto.response.exchangedata.coinex.depth.CoinExDepth;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepthAsk;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepthBid;

import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class CoinExCoinDepthBuilder {

    public static CoinDepth getCoinDepth(String coinName, CoinExDepth depth) {
        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(coinName);

        Set<CoinDepthAsk> coinDepthAskSet = depth.getAsks().stream()
                .map(ask -> {
                    CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
                    coinDepthAsk.setPrice(new BigDecimal(ask.getFirst()));
                    coinDepthAsk.setVolume(new BigDecimal(ask.getLast()));
                    return coinDepthAsk;
                })
                .collect(Collectors.toSet());

        Set<CoinDepthBid> coinDepthBidSet = depth.getBids().stream()
                .map(bid -> {
                    CoinDepthBid coinDepthBid = new CoinDepthBid();
                    coinDepthBid.setPrice(new BigDecimal(bid.getFirst()));
                    coinDepthBid.setVolume(new BigDecimal(bid.getLast()));
                    return coinDepthBid;
                })
                .collect(Collectors.toSet());

        if (coinDepthAskSet.isEmpty() || coinDepthBidSet.isEmpty()) {
            coinDepth.setStatusCode(404);
        } else {
            coinDepth.setStatusCode(200);
        }
        coinDepth.setCoinDepthAsks(new TreeSet<>(coinDepthAskSet));
        coinDepth.setCoinDepthBids(new TreeSet<>(coinDepthBidSet));

        return coinDepth;
    }
}
