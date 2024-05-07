package com.exchange.scanner.service.adapter;

import com.exchange.scanner.model.CoinData;

import java.util.List;

public class BinanceAdapter implements ExchangeAdapter {
    @Override
    public List<CoinData> fetchData(List<String> coins) {
        return List.of();
    }
}
