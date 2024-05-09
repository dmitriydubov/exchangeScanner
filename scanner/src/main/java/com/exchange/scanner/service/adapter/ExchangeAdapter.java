package com.exchange.scanner.service.adapter;

import com.exchange.scanner.dto.MarketSettings;
import com.exchange.scanner.dto.CoinData;
import java.util.List;

public interface ExchangeAdapter {
    List<CoinData> fetchBinanceData(MarketSettings marketSettings);

    List<CoinData> fetchGateIOData(MarketSettings marketSettings);

    List<CoinData> fetchMexcData(MarketSettings marketSettings);

    List<CoinData> fetchBybitData(MarketSettings marketSettings);

    List<CoinData> fetchKucoinData(MarketSettings marketSettings);

    List<CoinData> fetchBitgetData(MarketSettings marketSettings);

    List<CoinData> fetchHuobiData(MarketSettings marketSettings);

    List<CoinData> fetchPoloniexData(MarketSettings marketSettings);

    List<CoinData> fetchOKXData(MarketSettings marketSettings);

    List<CoinData> fetchBitmartData(MarketSettings marketSettings);

    List<CoinData> fetchLBankData(MarketSettings marketSettings);

    List<CoinData> fetchCoinexData(MarketSettings marketSettings);

    List<CoinData> fetchCoinWData(MarketSettings marketSettings);

    List<CoinData> fetchXTData(MarketSettings marketSettings);

    List<CoinData> fetchProbitData(MarketSettings marketSettings);

    List<CoinData> fetchBingXData(MarketSettings marketSettings);
}
