package com.exchange.scanner.services.utils.Probit;

import com.exchange.scanner.dto.response.exchangedata.probit.depth.ProbitDepthData;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepthAsk;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepthBid;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ProbitCoinDepthBuilder {

    public static CoinDepth getCoinDepth(String coinName, List<ProbitDepthData> data) {
        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(coinName);

        Set<CoinDepthAsk> coinDepthAskSet = data.stream()
            .filter(ask -> ask.getSide().equals("buy"))
            .map(ask -> {
                CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
                coinDepthAsk.setPrice(ask.getPrice());
                coinDepthAsk.setVolume(ask.getQuantity());
                return coinDepthAsk;
            })
            .collect(Collectors.toSet());

        Set<CoinDepthBid> coinDepthBidSet = data.stream()
            .filter(bid -> bid.getSide().equals("sell"))
            .map(bid -> {
                CoinDepthBid coinDepthBid = new CoinDepthBid();
                coinDepthBid.setPrice(bid.getPrice());
                coinDepthBid.setVolume(bid.getQuantity());
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
