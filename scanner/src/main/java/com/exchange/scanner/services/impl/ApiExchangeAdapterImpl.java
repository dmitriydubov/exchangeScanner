package com.exchange.scanner.services.impl;

import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.ApiExchangeAdapter;
import com.exchange.scanner.services.impl.api.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.Set;

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
}
