package com.exchange.scanner.services;

import com.exchange.scanner.dto.response.CoinInfoDTO;
import com.exchange.scanner.model.Coin;

import java.util.Set;

public interface CoinMarketCapService {

    Set<CoinInfoDTO> getCoinMarketCapCoinsInfo(Set<String> coins);
}
