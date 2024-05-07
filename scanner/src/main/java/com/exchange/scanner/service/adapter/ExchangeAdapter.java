package com.exchange.scanner.service.adapter;

import com.exchange.scanner.model.CoinData;

import java.util.List;

public interface ExchangeAdapter {
    List<CoinData> fetchData(List<String> coins);
}
