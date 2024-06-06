package com.exchange.scanner.services.impl;

import com.exchange.scanner.dto.response.exchangedata.coinsdata.CoinDataTicker;
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
            case "Binance" -> coins.addAll(apiBinance.getAllCoins());
            case "Gate.io" -> coins.addAll(apiGateIO.getAllCoins());
            case "MEXC" -> coins.addAll(apiMEXC.getAllCoins());
            case "Bybit" -> coins.addAll(apiBybit.getAllCoins());
            case "Kucoin" -> coins.addAll(apiKucoin.getAllCoins());
            case "Bitget" -> coins.addAll(apiBitget.getAllCoins());
            case "Huobi" -> coins.addAll(apiHuobi.getAllCoins());
            case "Poloniex" -> coins.addAll(apiPoloniex.getAllCoins());
            case "OKX" -> coins.addAll(apiOKX.getAllCoins());
            case "Bitmart" -> coins.addAll(apiBitmart.getAllCoins());
            case "LBank" -> coins.addAll(apiLBank.getAllCoins());
            case "CoinEx" -> coins.addAll(apiCoinEx.getAllCoins());
            case "CoinW" -> coins.addAll(apiCoinW.getAllCoins()); //Долгий запрос
            case "XT" -> coins.addAll(apiXT.getAllCoins());
            case "Probit" -> coins.addAll(apiProbit.getAllCoins());
            case "BingX" -> coins.addAll(apiBingX.getAllCoins());
        }

        return coins;
    }

    @Override
    public Map<String, List<CoinDataTicker>> getCoinPrice(String exchangeName, Set<Coin> coins) {

        Map<String, List<CoinDataTicker>> coinPriceMap = new HashMap<>();

        switch (exchangeName) {
            case "Binance" -> coinPriceMap.putAll(apiBinance.getCoinDataTicker(coins)); // Нет api по комиссиям
            case "Gate.io" -> coinPriceMap.putAll(apiGateIO.getCoinDataTicker(coins));
            case "MEXC" -> coinPriceMap.putAll(apiMEXC.getCoinDataTicker(coins));
            case "Bybit" -> coinPriceMap.putAll(apiBybit.getCoinDataTicker(coins)); // Нет api по комиссиям
            case "Kucoin" ->  coinPriceMap.putAll(apiKucoin.getCoinDataTicker(coins)); // Есть api по всем комиссиям
            case "Bitget" -> coinPriceMap.putAll(apiBitget.getCoinDataTicker(coins));
            case "Huobi" -> coinPriceMap.putAll(apiHuobi.getCoinDataTicker(coins));
            case "Poloniex" -> coinPriceMap.putAll(apiPoloniex.getCoinDataTicker(coins));
            case "OKX" -> coinPriceMap.putAll(apiOKX.getCoinDataTicker(coins));
            case "Bitmart" -> coinPriceMap.putAll(apiBitmart.getCoinDataTicker(coins));
            case "LBank" -> coinPriceMap.putAll(apiLBank.getCoinDataTicker(coins));
            case "CoinEx" -> coinPriceMap.putAll(apiCoinEx.getCoinDataTicker(coins));
            case "CoinW" -> coinPriceMap.putAll(apiCoinW.getCoinDataTicker(coins));
            case "XT" -> coinPriceMap.putAll(apiXT.getCoinDataTicker(coins));
            case "Probit" -> coinPriceMap.putAll(apiProbit.getCoinDataTicker(coins));
            case "BingX" -> coinPriceMap.putAll(apiBingX.getCoinDataTicker(coins)); // Нет api по комиссиям
        }

        return coinPriceMap;
    }
}
