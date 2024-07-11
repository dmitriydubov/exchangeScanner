package com.exchange.scanner.services.utils.CoinW;

import com.exchange.scanner.dto.response.exchangedata.coinw.depth.CoinWDepthData;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepthAsk;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepthBid;
import com.exchange.scanner.model.Coin;

import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class CoinWCoinDepthBuilder {

    public static CoinDepth getCoinDepth(Coin coin, CoinWDepthData data, String exchange) {
        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setExchange(exchange);
        coinDepth.setCoin(coin);
        coinDepth.setSlug(coin.getName() + "-" + exchange);

        Set<CoinDepthAsk> coinDepthAsks = data.getAsks().stream().map(ask -> {
                CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
                coinDepthAsk.setPrice(new BigDecimal(ask.getFirst()));
                coinDepthAsk.setVolume(new BigDecimal(ask.getLast()));
                return coinDepthAsk;
            })
            .collect(Collectors.toSet());

        Set<CoinDepthBid> coinDepthBids = data.getBids().stream().map(bid -> {
                CoinDepthBid coinDepthBid = new CoinDepthBid();
                coinDepthBid.setPrice(new BigDecimal(bid.getFirst()));
                coinDepthBid.setVolume(new BigDecimal(bid.getLast()));
                return coinDepthBid;
            })
            .collect(Collectors.toSet());

        if (coinDepthAsks.isEmpty() || coinDepthBids.isEmpty()) {
            coinDepth.setStatusCode(404);
        } else {
            coinDepth.setStatusCode(200);
        }
        coinDepth.setCoinDepthAsks(new TreeSet<>(coinDepthAsks));
        coinDepth.setCoinDepthBids(new TreeSet<>(coinDepthBids));

        return coinDepth;
    }
}
