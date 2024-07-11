package com.exchange.scanner.services.impl;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.ApiExchangeAdapter;
import com.exchange.scanner.services.impl.api.exchanges.*;
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
            case "Binance" -> coins.addAll(apiBinance.getAllCoins(exchange));
            case "Gate.io" -> coins.addAll(apiGateIO.getAllCoins(exchange));
            case "MEXC" -> coins.addAll(apiMEXC.getAllCoins(exchange));
            case "Bybit" -> coins.addAll(apiBybit.getAllCoins(exchange));
            case "Kucoin" -> coins.addAll(apiKucoin.getAllCoins(exchange));
            case "Bitget" -> coins.addAll(apiBitget.getAllCoins(exchange));
            case "Huobi" -> coins.addAll(apiHuobi.getAllCoins(exchange));
            case "Poloniex" -> coins.addAll(apiPoloniex.getAllCoins(exchange));
            case "OKX" -> coins.addAll(apiOKX.getAllCoins(exchange));
            case "Bitmart" -> coins.addAll(apiBitmart.getAllCoins(exchange));
            case "LBank" -> coins.addAll(apiLBank.getAllCoins(exchange));
            case "CoinEx" -> coins.addAll(apiCoinEx.getAllCoins(exchange));
            case "CoinW" -> coins.addAll(apiCoinW.getAllCoins(exchange));
            case "XT" -> coins.addAll(apiXT.getAllCoins(exchange));
            case "Probit" -> coins.addAll(apiProbit.getAllCoins(exchange));
            case "BingX" -> coins.addAll(apiBingX.getAllCoins(exchange));
        }

        return coins;
    }

    @Override
    public Set<CoinDepth> getOrderBook(Exchange exchange, Set<Coin> coins) {
        Set<CoinDepth> depthSet = new HashSet<>();

        switch (exchange.getName()) {
            case "Binance" -> depthSet.addAll(apiBinance.getOrderBook(coins, exchange.getName()));
            case "Gate.io" -> depthSet.addAll(apiGateIO.getOrderBook(coins, exchange.getName()));
            case "MEXC" -> depthSet.addAll(apiMEXC.getOrderBook(coins, exchange.getName()));
            case "Bybit" -> depthSet.addAll(apiBybit.getOrderBook(coins, exchange.getName()));
            case "Kucoin" ->  depthSet.addAll(apiKucoin.getOrderBook(coins, exchange.getName()));
            case "Bitget" -> depthSet.addAll(apiBitget.getOrderBook(coins, exchange.getName()));
            case "Huobi" -> depthSet.addAll(apiHuobi.getOrderBook(coins, exchange.getName()));
            case "Poloniex" -> depthSet.addAll(apiPoloniex.getOrderBook(coins, exchange.getName()));
            case "OKX" -> depthSet.addAll(apiOKX.getOrderBook(coins, exchange.getName()));
            case "Bitmart" -> depthSet.addAll(apiBitmart.getOrderBook(coins, exchange.getName()));
            case "LBank" -> depthSet.addAll(apiLBank.getOrderBook(coins, exchange.getName()));
            case "CoinEx" -> depthSet.addAll(apiCoinEx.getOrderBook(coins, exchange.getName()));
            case "CoinW" -> depthSet.addAll(apiCoinW.getOrderBook(coins, exchange.getName()));
            case "XT" -> depthSet.addAll(apiXT.getOrderBook(coins, exchange.getName()));
            case "Probit" -> depthSet.addAll(apiProbit.getOrderBook(coins, exchange.getName()));
            case "BingX" -> depthSet.addAll(apiBingX.getOrderBook(coins, exchange.getName()));
        }

        return depthSet;
    }

    @Override
    public Set<ChainResponseDTO> getCoinChain(String exchange, Set<Coin> coinsSet) {
        Set<ChainResponseDTO> result = new HashSet<>();

        switch (exchange) {
            case "Binance" -> result.addAll(apiBinance.getCoinChain(coinsSet, exchange));
            case "Gate.io" -> result.addAll(apiGateIO.getCoinChain(coinsSet, exchange)); //нет информации по комиссии сети
            case "MEXC" -> result.addAll(apiMEXC.getCoinChain(coinsSet, exchange));
            case "Bybit" -> result.addAll(apiBybit.getCoinChain(coinsSet, exchange));
            case "Kucoin" ->  result.addAll(apiKucoin.getCoinChain(coinsSet, exchange));
            case "Bitget" -> result.addAll(apiBitget.getCoinChain(coinsSet, exchange));
            case "Huobi" -> result.addAll(apiHuobi.getCoinChain(coinsSet, exchange));
            case "Poloniex" -> result.addAll(apiPoloniex.getCoinChain(coinsSet, exchange));
            case "OKX" -> result.addAll(apiOKX.getCoinChain(coinsSet, exchange));
            case "Bitmart" -> result.addAll(apiBitmart.getCoinChain(coinsSet, exchange));
            case "LBank" -> result.addAll(apiLBank.getCoinChain(coinsSet, exchange));
            case "CoinEx" -> result.addAll(apiCoinEx.getCoinChain(coinsSet, exchange));
            case "CoinW" -> result.addAll(apiCoinW.getCoinChain(coinsSet, exchange));
            case "XT" -> result.addAll(apiXT.getCoinChain(coinsSet, exchange));
            case "Probit" -> result.addAll(apiProbit.getCoinChain(coinsSet, exchange));
            case "BingX" -> result.addAll(apiBingX.getCoinChain(coinsSet, exchange));
        }

        return result;
    }

    @Override
    public Set<TradingFeeResponseDTO> getTradingFee(String exchange, Set<Coin> coinsSet) {
        Set<TradingFeeResponseDTO> result = new HashSet<>();

        switch (exchange) {
            case "Binance" -> result.addAll(apiBinance.getTradingFee(coinsSet, exchange));
            case "Gate.io" -> result.addAll(apiGateIO.getTradingFee(coinsSet, exchange));
            case "MEXC" -> result.addAll(apiMEXC.getTradingFee(coinsSet, exchange));
            case "Bybit" -> result.addAll(apiBybit.getTradingFee(coinsSet, exchange));
            case "Kucoin" ->  result.addAll(apiKucoin.getTradingFee(coinsSet, exchange));
            case "Bitget" -> result.addAll(apiBitget.getTradingFee(coinsSet, exchange));
            case "Huobi" -> result.addAll(apiHuobi.getTradingFee(coinsSet, exchange));
            case "Poloniex" -> result.addAll(apiPoloniex.getTradingFee(coinsSet, exchange));
            case "OKX" -> result.addAll(apiOKX.getTradingFee(coinsSet, exchange));
            case "Bitmart" -> result.addAll(apiBitmart.getTradingFee(coinsSet, exchange));
            case "LBank" -> result.addAll(apiLBank.getTradingFee(coinsSet, exchange));
            case "CoinEx" -> result.addAll(apiCoinEx.getTradingFee(coinsSet, exchange));
            case "CoinW" -> result.addAll(apiCoinW.getTradingFee(coinsSet, exchange));
            case "XT" -> result.addAll(apiXT.getTradingFee(coinsSet, exchange)); //XT Не предоставляет торговые комиссии
            case "Probit" -> result.addAll(apiProbit.getTradingFee(coinsSet, exchange));
            case "BingX" -> result.addAll(apiBingX.getTradingFee(coinsSet, exchange));
        }

        return result;
    }

    @Override
    public Set<Volume24HResponseDTO> getCoinVolume24h(String exchangeName, Set<Coin> coins) {

        Set<Volume24HResponseDTO> result = new HashSet<>();

        switch (exchangeName) {
            case "Binance" -> result.addAll(apiBinance.getCoinVolume24h(coins, exchangeName));
            case "Gate.io" -> result.addAll(apiGateIO.getCoinVolume24h(coins, exchangeName));
            case "MEXC" -> result.addAll(apiMEXC.getCoinVolume24h(coins, exchangeName));
            case "Bybit" -> result.addAll(apiBybit.getCoinVolume24h(coins, exchangeName));
            case "Kucoin" ->  result.addAll(apiKucoin.getCoinVolume24h(coins, exchangeName));
            case "Bitget" -> result.addAll(apiBitget.getCoinVolume24h(coins, exchangeName));
            case "Huobi" -> result.addAll(apiHuobi.getCoinVolume24h(coins, exchangeName));
            case "Poloniex" -> result.addAll(apiPoloniex.getCoinVolume24h(coins, exchangeName));
            case "OKX" -> result.addAll(apiOKX.getCoinVolume24h(coins, exchangeName));
            case "Bitmart" -> result.addAll(apiBitmart.getCoinVolume24h(coins, exchangeName));
            case "LBank" -> result.addAll(apiLBank.getCoinVolume24h(coins, exchangeName));
            case "CoinEx" -> result.addAll(apiCoinEx.getCoinVolume24h(coins, exchangeName));
            case "CoinW" -> result.addAll(apiCoinW.getCoinVolume24h(coins, exchangeName));
            case "XT" -> result.addAll(apiXT.getCoinVolume24h(coins, exchangeName));
            case "Probit" -> result.addAll(apiProbit.getCoinVolume24h(coins, exchangeName));
            case "BingX" -> result.addAll(apiBingX.getCoinVolume24h(coins, exchangeName));
        }

        return result;
    }
}
