package com.exchange.scanner.dto.response.exchangedata.bitget.tradingfee;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Data {

    private String symbol;

    private String takerFeeRate;
}
