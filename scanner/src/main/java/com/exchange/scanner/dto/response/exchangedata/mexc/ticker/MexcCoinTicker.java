package com.exchange.scanner.dto.response.exchangedata.mexc.ticker;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MexcCoinTicker {

    private String symbol;

    private String volume;

    private String bidPrice;

    private String askPrice;
}
