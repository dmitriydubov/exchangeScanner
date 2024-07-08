package com.exchange.scanner.services;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ApiExchangeAdapter {
    Set<Coin> refreshExchangeCoins(Exchange exchange);

    Set<Volume24HResponseDTO> getCoinVolume24h(String exchangeName, Set<Coin> coins);

    Set<CoinDepth> getOrderBook(Exchange exchange, Set<String> coins);

    Set<ChainResponseDTO> getCoinChain(String exchange, Set<Coin> coinsSet);

    Set<TradingFeeResponseDTO> getTradingFee(String exchange, Set<Coin> coinsSet);
}
