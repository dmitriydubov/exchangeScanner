package com.exchange.scanner.services.utils.Poloniex;

import com.exchange.scanner.dto.response.exchangedata.poloniex.depth.PoloniexCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepthAsk;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepthBid;
import com.exchange.scanner.model.Coin;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class PoloniexCoinDepthBuilder {

    public static CoinDepth getPoloniexCoinDepth(PoloniexCoinDepth depth, Coin coin, String exchange) {
        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setExchange(exchange);
        coinDepth.setCoin(coin);
        coinDepth.setSlug(coin.getName() + "-" + exchange);

        Set<CoinDepthAsk> coinDepthAskSet = depth.getData().getFirst().getAsks().stream()
                .map(ask -> {
                    CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
                    coinDepthAsk.setPrice(new BigDecimal(ask.getFirst()));
                    coinDepthAsk.setVolume(new BigDecimal(ask.getLast()));
                    return coinDepthAsk;
                })
                .collect(Collectors.toSet());

        Set<CoinDepthBid> coinDepthBidSet = depth.getData().getFirst().getBids().stream()
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
        coinDepth.setCoinDepthBids(new TreeSet<>(coinDepthBidSet));
        coinDepth.setCoinDepthAsks(new TreeSet<>(coinDepthAskSet));

        return coinDepth;
    }
}
