package com.exchange.scanner.dto.response.exchangedata.poloniex.ticker;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PoloniexTicker {

    private String symbol;

    private String amount;

    private String bid;

    private String ask;
}
