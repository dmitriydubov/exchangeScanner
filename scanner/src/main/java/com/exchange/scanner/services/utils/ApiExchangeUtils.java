package com.exchange.scanner.services.utils;

import com.exchange.scanner.dto.response.exchangedata.binance.depth.BinanceCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bingx.depth.BingXCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bitget.depth.BitgetCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bitmart.depth.BitmartCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.bybit.depth.BybitCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.coinex.depth.CoinExCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.coinw.depth.CoinWCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.gateio.depth.GateIOCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.huobi.depth.HuobiCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.kucoin.depth.KucoinCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.lbank.depth.LBankCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.mexc.depth.MexcCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.okx.depth.OKXCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.poloniex.depth.PoloniexCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.probit.depth.ProbitCoinDepth;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepthAsk;
import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepthBid;
import com.exchange.scanner.dto.response.exchangedata.xt.depth.XTCoinDepth;

import java.util.*;
import java.util.stream.Collectors;

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

    public static CoinDepth getGateIOCoinDepth(GateIOCoinDepth depth) {

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

    public static CoinDepth getMexcCoinDepth(MexcCoinDepth depth) {

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

    public static CoinDepth getBybitCoinDepth(BybitCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getResult().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getResult().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getKucoinCoinDepth(KucoinCoinDepth depth) {

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

    public static CoinDepth getBitgetCoinDepth(BitgetCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        Set<CoinDepthBid> coinDepthBids = depth.getData().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getHuobiCoinDepth(HuobiCoinDepth depth) {
        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getTick().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getTick().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getPoloniexCoinDepth(PoloniexCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = new HashSet<>();
        for (int i = 0; i < depth.getBids().size() / 2; i++) {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            if (i % 2 == 0) {
                coinDepthBid.setPrice(depth.getBids().get(i));
            } else {
                coinDepthBid.setVolume(depth.getBids().get(i));
            }
            coinDepthBids.add(coinDepthBid);
        }

        Set<CoinDepthAsk> coinDepthAsks =new HashSet<>();
        for (int i = 0; i < depth.getAsks().size() / 2; i++) {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            if (i % 2 == 0) {
                coinDepthAsk.setPrice(depth.getBids().get(i));
            } else {
                coinDepthAsk.setVolume(depth.getBids().get(i));
            }
            coinDepthAsks.add(coinDepthAsk);
        }

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getOKXCoinDepth(OKXCoinDepth depth) {
        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getData().getFirst().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().getFirst().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getBitmartCoinDepth(BitmartCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        String coinName = depth.getData().getSymbol().replaceAll("_USDT", "");
        coinDepth.setCoinName(coinName);

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

    public static CoinDepth getLBankCoinDepth(LBankCoinDepth depth) {

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

    public static CoinDepth getCoinExCoinDepth(CoinExCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        String coinName = depth.getData().getMarket().replaceAll("USDT", "");
        coinDepth.setCoinName(coinName);

        Set<CoinDepthBid> coinDepthBids = depth.getData().getDepth().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().getDepth().getBids().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getCoinWCoinDepth(CoinWCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getData().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.getPrice());
            coinDepthBid.setVolume(value.getAmount());
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.getPrice());
            coinDepthAsk.setVolume(value.getAmount());
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getXTCoinDepth(XTCoinDepth depth) {
        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getResult().getBids().stream().map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.get(0));
            coinDepthBid.setVolume(value.get(1));
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getResult().getAsks().stream().map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.get(0));
            coinDepthAsk.setVolume(value.get(1));
            return coinDepthAsk;
        }).collect(Collectors.toSet());

        coinDepth.setCoinDepthBids(coinDepthBids);
        coinDepth.setCoinDepthAsks(coinDepthAsks);

        return coinDepth;
    }

    public static CoinDepth getProbitCoinDepth(ProbitCoinDepth depth) {

        CoinDepth coinDepth = new CoinDepth();
        coinDepth.setCoinName(depth.getCoinName());

        Set<CoinDepthBid> coinDepthBids = depth.getData().stream().filter(data -> data.getSide().equals("buy")).map(value -> {
            CoinDepthBid coinDepthBid = new CoinDepthBid();
            coinDepthBid.setPrice(value.getPrice());
            coinDepthBid.setVolume(value.getQuantity());
            return coinDepthBid;
        }).collect(Collectors.toSet());

        Set<CoinDepthAsk> coinDepthAsks = depth.getData().stream().filter(data -> data.getSide().equals("sell")).map(value -> {
            CoinDepthAsk coinDepthAsk = new CoinDepthAsk();
            coinDepthAsk.setPrice(value.getPrice());
            coinDepthAsk.setVolume(value.getQuantity());
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

    public static String getCoinWSymbolNumber(String responseRaw, String coinName) {

        int coinNameIndex = responseRaw.indexOf(coinName);
        int startIndex = responseRaw.lastIndexOf(" ", coinNameIndex) + 1;

        return responseRaw.substring(startIndex, coinNameIndex - 1);
    }
}
