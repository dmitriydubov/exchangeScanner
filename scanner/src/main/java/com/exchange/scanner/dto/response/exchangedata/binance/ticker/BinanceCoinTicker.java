package com.exchange.scanner.dto.response.exchangedata.binance.ticker;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BinanceCoinTicker {

    private String symbol;

    private String volume;

    private String bidPrice;

    private String askPrice;
}
