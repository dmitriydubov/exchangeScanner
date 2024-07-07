package com.exchange.scanner.dto.response.exchangedata.bybit.tradingfee;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BybitTradingFeeList {

    private String symbol;

    private String takerFeeRate;
}
