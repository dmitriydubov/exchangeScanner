package com.exchange.scanner.service;

import com.exchange.scanner.model.CoinData;
import com.exchange.scanner.service.adapter.ExchangeAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExchangeFacade {
    private final List<ExchangeAdapter> exchangeAdapters;
    private final Logger logger = LoggerFactory.getLogger(ExchangeFacade.class);

    @Autowired
    public ExchangeFacade(List<ExchangeAdapter> exchangeAdapters) {
        this.exchangeAdapters = exchangeAdapters;
    }

    public List<CoinData> getDataFromExchanges(List<String> coins) {
        List<CoinData> coinDataList = new ArrayList<>();
        exchangeAdapters.forEach(exchangeAdapter -> {
            coinDataList.addAll(exchangeAdapter.fetchData(coins));
        });
        return coinDataList;
    }
}
