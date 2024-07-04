package com.exchange.scanner.dto.response.exchangedata.poloniex.tradingfee;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PoloniexFeeRate {

    private String symbol;

    private String takerRate;
}
