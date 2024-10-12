package com.exchange.scanner.services;

import com.exchange.scanner.dto.response.ChainResponseDTO;
import com.exchange.scanner.dto.response.TradingFeeResponseDTO;
import com.exchange.scanner.dto.response.Volume24HResponseDTO;
import com.exchange.scanner.model.Coin;
import com.exchange.scanner.model.Exchange;

import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.locks.ReentrantLock;

public interface ApiExchangeAdapter {
    Set<Coin> refreshExchangeCoins(Exchange exchange);

    Set<Volume24HResponseDTO> getCoinVolume24h(String exchangeName, Set<Coin> coins);

    void getOrderBook(Exchange exchange, Set<Coin> coins, BlockingDeque<Runnable> taskQueue, ReentrantLock lock);

    Set<ChainResponseDTO> getCoinChain(String exchange, Set<Coin> coinsSet);

    Set<TradingFeeResponseDTO> getTradingFee(String exchange, Set<Coin> coinsSet);
}
