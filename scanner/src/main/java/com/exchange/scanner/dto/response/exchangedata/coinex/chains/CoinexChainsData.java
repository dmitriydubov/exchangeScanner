package com.exchange.scanner.dto.response.exchangedata.coinex.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CoinexChainsData {

    private CoinexChainAsset asset;

    private List<CoinexChain> chains;
}
