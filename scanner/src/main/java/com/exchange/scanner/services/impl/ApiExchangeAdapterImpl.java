package com.exchange.scanner.services.impl;

import com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.ApiExchangeAdapter;
import com.exchange.scanner.services.impl.api.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ApiExchangeAdapterImpl implements ApiExchangeAdapter {

    private final ApiBinance apiBinance;
    private final ApiGateIO apiGateIO;
    private final ApiMEXC apiMEXC;
    private final ApiBybit apiBybit;
    private final ApiKucoin apiKucoin;
    private final ApiBitget apiBitget;
    private final ApiHuobi apiHuobi;
    private final ApiPoloniex apiPoloniex;
    private final ApiOKX apiOKX;
    private final ApiBitmart apiBitmart;
    private final ApiLBank apiLBank;
    private final ApiCoinEx apiCoinEx;
    private final ApiCoinW apiCoinW;
    private final ApiXT apiXT;
    private final ApiProbit apiProbit;
    private final ApiBingX apiBingX;

    @Override
    public Set<Coin> refreshExchangeCoins(Exchange exchange) {

        Set<Coin> coins = new HashSet<>();

        switch (exchange.getName()) {
//            case "Binance" -> coins.addAll(apiBinance.getAllCoins());
            case "Gate.io" -> coins.addAll(apiGateIO.getAllCoins()); //1
            case "MEXC" -> coins.addAll(apiMEXC.getAllCoins()); //5
            case "Bybit" -> coins.addAll(apiBybit.getAllCoins()); //6
            case "Kucoin" -> coins.addAll(apiKucoin.getAllCoins()); //2
            case "Bitget" -> coins.addAll(apiBitget.getAllCoins()); //3
            case "Huobi" -> coins.addAll(apiHuobi.getAllCoins()); //7
            case "Poloniex" -> coins.addAll(apiPoloniex.getAllCoins()); //8
            case "OKX" -> coins.addAll(apiOKX.getAllCoins()); //9
            case "Bitmart" -> coins.addAll(apiBitmart.getAllCoins()); //10
            case "LBank" -> coins.addAll(apiLBank.getAllCoins()); //11
            case "CoinEx" -> coins.addAll(apiCoinEx.getAllCoins()); //12
            case "CoinW" -> coins.addAll(apiCoinW.getAllCoins()); //13
            case "XT" -> coins.addAll(apiXT.getAllCoins()); //14
            case "Probit" -> coins.addAll(apiProbit.getAllCoins()); //4
//            case "BingX" -> coins.addAll(apiBingX.getAllCoins());
        }

        return coins;
    }

    @Override
    public Set<CoinDepth> getOrderBook(Exchange exchange, Set<String> coins) {
        Set<CoinDepth> ordersBook = new HashSet<>();

        switch (exchange.getName()) {
//            case "Binance" -> ordersBook.addAll(apiBinance.getOrderBook(coins));
            case "Gate.io" -> ordersBook.addAll(apiGateIO.getOrderBook(coins)); //1
            case "MEXC" -> ordersBook.addAll(apiMEXC.getOrderBook(coins)); //5
            case "Bybit" -> ordersBook.addAll(apiBybit.getOrderBook(coins)); //6
            case "Kucoin" ->  ordersBook.addAll(apiKucoin.getOrderBook(coins)); //2
            case "Bitget" -> ordersBook.addAll(apiBitget.getOrderBook(coins)); //3
            case "Huobi" -> ordersBook.addAll(apiHuobi.getOrderBook(coins)); //7
            case "Poloniex" -> ordersBook.addAll(apiPoloniex.getOrderBook(coins)); //8
            case "OKX" -> ordersBook.addAll(apiOKX.getOrderBook(coins)); //9
            case "Bitmart" -> ordersBook.addAll(apiBitmart.getOrderBook(coins)); //10
            case "LBank" -> ordersBook.addAll(apiLBank.getOrderBook(coins)); //11
            case "CoinEx" -> ordersBook.addAll(apiCoinEx.getOrderBook(coins)); //12
            case "CoinW" -> ordersBook.addAll(apiCoinW.getOrderBook(coins)); //13
            case "XT" -> ordersBook.addAll(apiXT.getOrderBook(coins)); //14
            case "Probit" -> ordersBook.addAll(apiProbit.getOrderBook(coins)); //4
//            case "BingX" -> ordersBook.addAll(apiBingX.getOrderBook(coins));
        }

        return ordersBook;
    }

    @Override
    public Map<String, Set<Coin>> getCoinChain(String exchange, Set<Coin> coinsSet) {
        Set<Coin> coins = new HashSet<>();

        switch (exchange) {
//            case "Binance" -> coins.addAll(apiBinance.getCoinChain(coinsSet));
            case "Gate.io" -> coins.addAll(apiGateIO.getCoinChain(coinsSet)); //1 нет информации по комиссии сети
            case "MEXC" -> coins.addAll(apiMEXC.getCoinChain(coinsSet)); //5
            case "Bybit" -> coins.addAll(apiBybit.getCoinChain(coinsSet)); //6
            case "Kucoin" ->  coins.addAll(apiKucoin.getCoinChain(coinsSet)); //2
            case "Bitget" -> coins.addAll(apiBitget.getCoinChain(coinsSet)); //3
            case "Huobi" -> coins.addAll(apiHuobi.getCoinChain(coinsSet)); //7
            case "Poloniex" -> coins.addAll(apiPoloniex.getCoinChain(coinsSet)); //8
            case "OKX" -> coins.addAll(apiOKX.getCoinChain(coinsSet)); //9
            case "Bitmart" -> coins.addAll(apiBitmart.getCoinChain(coinsSet)); //10
            case "LBank" -> coins.addAll(apiLBank.getCoinChain(coinsSet)); //11
            case "CoinEx" -> coins.addAll(apiCoinEx.getCoinChain(coinsSet)); //12
            case "CoinW" -> coins.addAll(apiCoinW.getCoinChain(coinsSet)); //13
            case "XT" -> coins.addAll(apiXT.getCoinChain(coinsSet)); //14
            case "Probit" -> coins.addAll(apiProbit.getCoinChain(coinsSet)); //4
//            case "BingX" -> coins.addAll(apiBingX.getCoinChain(coinsSet));
        }

        return Collections.singletonMap(exchange, coins);
    }

    @Override
    public Map<String, Set<Coin>> getTradingFee(String exchange, Set<Coin> coinsSet) {
        Set<Coin> coins = new HashSet<>();

        switch (exchange) {
//            case "Binance" -> coins.addAll(apiBinance.getTradingFee(coinsSet));
            case "Gate.io" -> coins.addAll(apiGateIO.getTradingFee(coinsSet)); //1
            case "MEXC" -> coins.addAll(apiMEXC.getTradingFee(coinsSet)); //5
            case "Bybit" -> coins.addAll(apiBybit.getTradingFee(coinsSet)); //6
            case "Kucoin" ->  coins.addAll(apiKucoin.getTradingFee(coinsSet)); //2
            case "Bitget" -> coins.addAll(apiBitget.getTradingFee(coinsSet)); //3
            case "Huobi" -> coins.addAll(apiHuobi.getTradingFee(coinsSet)); //7
            case "Poloniex" -> coins.addAll(apiPoloniex.getTradingFee(coinsSet)); //8
            case "OKX" -> coins.addAll(apiOKX.getTradingFee(coinsSet)); //9
            case "Bitmart" -> coins.addAll(apiBitmart.getTradingFee(coinsSet)); //10
            case "LBank" -> coins.addAll(apiLBank.getTradingFee(coinsSet)); //11
            case "CoinEx" -> coins.addAll(apiCoinEx.getTradingFee(coinsSet)); //12
            case "CoinW" -> coins.addAll(apiCoinW.getTradingFee(coinsSet)); //13
            case "XT" -> coins.addAll(apiXT.getTradingFee(coinsSet)); //XT Не предоставляет торговые комиссии
            case "Probit" -> coins.addAll(apiProbit.getTradingFee(coinsSet)); //4
//            case "BingX" -> coins.addAll(apiBingX.getTradingFee(coinsSet));
        }

        return Collections.singletonMap(exchange, coins);
    }

    @Override
    public Map<String, Set<Coin>> getCoinVolume24h(String exchangeName, Set<Coin> coins) {

        Set<Coin> result = new HashSet<>();

        switch (exchangeName) {
            case "Binance" -> result.addAll(apiBinance.getCoinVolume24h(coins));
            case "Gate.io" -> result.addAll(apiGateIO.getCoinVolume24h(coins));
            case "MEXC" -> result.addAll(apiMEXC.getCoinVolume24h(coins));
            case "Bybit" -> result.addAll(apiBybit.getCoinVolume24h(coins));
            case "Kucoin" ->  result.addAll(apiKucoin.getCoinVolume24h(coins));
            case "Bitget" -> result.addAll(apiBitget.getCoinVolume24h(coins));
            case "Huobi" -> result.addAll(apiHuobi.getCoinVolume24h(coins));
            case "Poloniex" -> result.addAll(apiPoloniex.getCoinVolume24h(coins));
            case "OKX" -> result.addAll(apiOKX.getCoinVolume24h(coins));
            case "Bitmart" -> result.addAll(apiBitmart.getCoinVolume24h(coins));
            case "LBank" -> result.addAll(apiLBank.getCoinVolume24h(coins));
            case "CoinEx" -> result.addAll(apiCoinEx.getCoinVolume24h(coins));
            case "CoinW" -> result.addAll(apiCoinW.getCoinVolume24h(coins));
            case "XT" -> result.addAll(apiXT.getCoinVolume24h(coins));
            case "Probit" -> result.addAll(apiProbit.getCoinVolume24h(coins));
            case "BingX" -> result.addAll(apiBingX.getCoinVolume24h(coins));
        }

        return Collections.singletonMap(exchangeName, result);
    }
}
