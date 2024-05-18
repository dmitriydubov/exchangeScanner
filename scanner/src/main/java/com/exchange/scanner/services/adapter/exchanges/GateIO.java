package com.exchange.scanner.services.adapter.exchanges;

import com.exchange.scanner.dto.MarketSettings;
import com.exchange.scanner.dto.CoinData;

import java.util.List;

public interface GateIO {
    List<CoinData> getTickerData(MarketSettings marketSettings);
}
