package com.exchange.scanner.dto.response.exchangedata.binance.coins;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BinanceCurrency {

    private String status;

    private String baseAsset;

    private String quoteAsset;

    private Boolean isSpotTradingAllowed;

    private Boolean isMarginTradingAllowed;
}
