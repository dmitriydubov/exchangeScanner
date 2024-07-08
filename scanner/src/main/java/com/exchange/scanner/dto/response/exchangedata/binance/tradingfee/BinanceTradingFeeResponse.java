package com.exchange.scanner.dto.response.exchangedata.binance.tradingfee;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class BinanceTradingFeeResponse {

    private String symbol;

    private String takerCommission;
}
