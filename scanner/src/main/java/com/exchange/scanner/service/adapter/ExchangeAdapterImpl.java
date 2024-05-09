package com.exchange.scanner.service.adapter;

import com.exchange.scanner.dto.MarketSettings;
import com.exchange.scanner.dto.CoinData;
import com.exchange.scanner.service.adapter.exchanges.Binance;
import com.exchange.scanner.service.adapter.exchanges.GateIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

@Component
public class ExchangeAdapterImpl implements ExchangeAdapter {
    private final Binance binance;
    private final GateIO gateIO;

    @Autowired
    public ExchangeAdapterImpl(Binance binance, GateIO gateIO) {
        this.binance = binance;
        this.gateIO = gateIO;
    }

    @Override
    public List<CoinData> fetchBinanceData(MarketSettings marketSettings) throws HttpClientErrorException {
        return binance.getTickerData(marketSettings);
    }

    @Override
    public List<CoinData> fetchGateIOData(MarketSettings marketSettings) {
        return gateIO.getTickerData(marketSettings);
    }

    @Override
    public List<CoinData> fetchMexcData(MarketSettings marketSettings) {
        return List.of();
    }

    @Override
    public List<CoinData> fetchBybitData(MarketSettings marketSettings) {
        return List.of();
    }

    @Override
    public List<CoinData> fetchKucoinData(MarketSettings marketSettings) {
        return List.of();
    }

    @Override
    public List<CoinData> fetchBitgetData(MarketSettings marketSettings) {
        return List.of();
    }

    @Override
    public List<CoinData> fetchHuobiData(MarketSettings marketSettings) {
        return List.of();
    }

    @Override
    public List<CoinData> fetchPoloniexData(MarketSettings marketSettings) {
        return List.of();
    }

    @Override
    public List<CoinData> fetchOKXData(MarketSettings marketSettings) {
        return List.of();
    }

    @Override
    public List<CoinData> fetchBitmartData(MarketSettings marketSettings) {
        return List.of();
    }

    @Override
    public List<CoinData> fetchLBankData(MarketSettings marketSettings) {
        return List.of();
    }

    @Override
    public List<CoinData> fetchCoinexData(MarketSettings marketSettings) {
        return List.of();
    }

    @Override
    public List<CoinData> fetchCoinWData(MarketSettings marketSettings) {
        return List.of();
    }

    @Override
    public List<CoinData> fetchXTData(MarketSettings marketSettings) {
        return List.of();
    }

    @Override
    public List<CoinData> fetchProbitData(MarketSettings marketSettings) {
        return List.of();
    }

    @Override
    public List<CoinData> fetchBingXData(MarketSettings marketSettings) {
        return List.of();
    }
}
