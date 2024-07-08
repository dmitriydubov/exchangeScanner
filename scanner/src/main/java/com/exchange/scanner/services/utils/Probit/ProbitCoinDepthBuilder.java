package com.exchange.scanner.services.utils.Probit;

import com.exchange.scanner.dto.response.exchangedata.probit.depth.ProbitDepthData;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepthAsk;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepthBid;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ProbitCoinDepthBuilder {

    public static CoinDepth getCoinDepth(String coinName, List<ProbitDepthData> data) {
        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(coinName);

        Set<CoinDepthAsk> coinDepthAskSet = data.stream()
            .filter(ask -> ask.getSide().equals("sell"))
            .map(ask -> {
                CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
                coinDepthAsk.setPrice(new BigDecimal(ask.getPrice()));
                coinDepthAsk.setVolume(new BigDecimal(ask.getQuantity()));
                return coinDepthAsk;
            })
            .collect(Collectors.toSet());

        Set<CoinDepthBid> coinDepthBidSet = data.stream()
            .filter(bid -> bid.getSide().equals("buy"))
            .map(bid -> {
                CoinDepthBid coinDepthBid = new CoinDepthBid();
                coinDepthBid.setPrice(new BigDecimal(bid.getPrice()));
                coinDepthBid.setVolume(new BigDecimal(bid.getQuantity()));
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
