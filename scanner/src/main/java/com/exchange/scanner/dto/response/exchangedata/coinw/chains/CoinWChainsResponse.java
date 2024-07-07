package com.exchange.scanner.dto.response.exchangedata.coinw.chains;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class CoinWChainsResponse {

    private String code;

    private Map<String, CoinWChain> data;
}
