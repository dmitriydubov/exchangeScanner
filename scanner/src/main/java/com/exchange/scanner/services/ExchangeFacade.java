package com.exchange.scanner.services;

import com.exchange.scanner.dto.MarketSettings;
import com.exchange.scanner.dto.CoinData;
import com.exchange.scanner.services.adapter.ExchangeAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExchangeFacade {
    private final ExchangeAdapter exchangeAdapter;
    private final Logger logger = LoggerFactory.getLogger(ExchangeFacade.class);

    @Autowired
    public ExchangeFacade(ExchangeAdapter exchangeAdapter) {
        this.exchangeAdapter = exchangeAdapter;
    }

    public List<CoinData> getDataFromExchanges(MarketSettings marketSettings) throws HttpClientErrorException {
        List<CoinData> coinDataList = new ArrayList<>();
        marketSettings.getMarketsBuy().forEach(exchange -> {
            switch (exchange) {
                case "binance" -> coinDataList.addAll(exchangeAdapter.fetchBinanceData(marketSettings));
                case "gateio" -> coinDataList.addAll(exchangeAdapter.fetchGateIOData(marketSettings));
                case "mexc" -> coinDataList.addAll(exchangeAdapter.fetchMexcData(marketSettings));
                case "bybit" -> coinDataList.addAll(exchangeAdapter.fetchBybitData(marketSettings));
                case "kucoin" -> coinDataList.addAll(exchangeAdapter.fetchKucoinData(marketSettings));
                case "bitget" -> coinDataList.addAll(exchangeAdapter.fetchBitgetData(marketSettings));
                case "huobi" -> coinDataList.addAll(exchangeAdapter.fetchHuobiData(marketSettings));
                case "poloniex" -> coinDataList.addAll(exchangeAdapter.fetchPoloniexData(marketSettings));
                case "okx" -> coinDataList.addAll(exchangeAdapter.fetchOKXData(marketSettings));
                case "bitmart" -> coinDataList.addAll(exchangeAdapter.fetchBitmartData(marketSettings));
                case "lbank" -> coinDataList.addAll(exchangeAdapter.fetchLBankData(marketSettings));
                case "coinex" -> coinDataList.addAll(exchangeAdapter.fetchCoinexData(marketSettings));
                case "coinw" -> coinDataList.addAll(exchangeAdapter.fetchCoinWData(marketSettings));
                case "xt" -> coinDataList.addAll(exchangeAdapter.fetchXTData(marketSettings));
                case "probit" -> coinDataList.addAll(exchangeAdapter.fetchProbitData(marketSettings));
                case "bingx" -> coinDataList.addAll(exchangeAdapter.fetchBingXData(marketSettings));
            }
        });

        return coinDataList;
    }
}
