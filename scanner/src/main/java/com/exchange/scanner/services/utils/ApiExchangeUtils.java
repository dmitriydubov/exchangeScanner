package com.exchange.scanner.services.utils;

import com.exchange.scanner.dto.response.exchangedata.binance.depth.BinanceCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bingx.depth.BingXCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.coinex.depth.CoinExCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepthAsk;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepthBid;
import com.exchange.scanner.dto.response.exchangedata.xt.depth.XTCoinDepth;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ApiExchangeUtils {

    public static CoinDepth getBinanceCoinDepth(BinanceCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getBingXCoinDepth(BingXCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getData().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }
}
