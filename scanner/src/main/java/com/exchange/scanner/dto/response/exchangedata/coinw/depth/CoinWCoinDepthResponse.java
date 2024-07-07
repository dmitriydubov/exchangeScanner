package com.exchange.scanner.dto.response.exchangedata.coinw.depth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinWCoinDepthResponse {

    private String code;

    private CoinWDepthData data;
}
