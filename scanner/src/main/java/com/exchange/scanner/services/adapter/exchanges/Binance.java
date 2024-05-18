package com.exchange.scanner.services.adapter.exchanges;

import com.exchange.scanner.dto.MarketSettings;
import com.exchange.scanner.dto.CoinData;

import java.util.List;

public interface Binance {
    List<CoinData> getTickerData(MarketSettings marketSettings);
}
