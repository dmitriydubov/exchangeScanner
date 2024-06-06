package com.exchange.scanner.dto.response.exchangedata.binance.exchangeinfo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Symbols {

    private String status;

    private String baseAsset;

    private String quoteAsset;

    private Boolean isSpotTradingAllowed;
}
