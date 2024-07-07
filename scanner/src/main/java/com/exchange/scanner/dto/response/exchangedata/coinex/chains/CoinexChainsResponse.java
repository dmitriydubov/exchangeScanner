package com.exchange.scanner.dto.response.exchangedata.coinex.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CoinexChainsResponse {

    private String code;

    private List<CoinexChainsData> data;

    private String message;
}
