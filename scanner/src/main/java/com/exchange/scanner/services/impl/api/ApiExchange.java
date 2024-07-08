package com.exchange.scanner.services.impl.api;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.dto.response.exchangedata.depth.coindepth.CoinDepth;
import com.exchange.scanner.model.Coin;

import java.util.Set;

public interface ApiExchange {
    Set<Coin> getAllCoins();

    Set<Volume24HResponseDTO> getCoinVolume24h(Set<Coin> coins, String exchangeName);

    Set<CoinDepth> getOrderBook(Set<String> coins);

    Set<ChainResponseDTO> getCoinChain(Set<Coin> coins, String exchangeName);

    Set<TradingFeeResponseDTO> getTradingFee(Set<Coin> coins, String exchange);
}
