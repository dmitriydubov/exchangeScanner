package com.exchange.scanner.services.utils.Binance;

import com.exchange.scanner.dto.response.exchangedata.binance.depth.BinanceCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepthAsk;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepthBid;
import com.exchange.scanner.model.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class BinanceCoinDepthBuilder {

    private static final Logger log = LoggerFactory.getLogger(BinanceCoinDepthBuilder.class);

    public static CoinDepth getCoinDepth(Coin coin, BinanceCoinDepth depth, String exchange) {
        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoin(coin);
        coinDepth.setExchange(exchange);
        coinDepth.setSlug(coin.getName() + "-" + exchange);

        Set<CoinDepthAsk> coinDepthAskSet = depth.getA().stream()
                .map(ask -> {
                    CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
                    coinDepthAsk.setPrice(new BigDecimal(ask.getFirst()));
                    coinDepthAsk.setVolume(new BigDecimal(ask.getLast()));
                    return coinDepthAsk;
                })
                .collect(Collectors.toSet());

        Set<CoinDepthBid> coinDepthBidSet = depth.getB().stream()
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
