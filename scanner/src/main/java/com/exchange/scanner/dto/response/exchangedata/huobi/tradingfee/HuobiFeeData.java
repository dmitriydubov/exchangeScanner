package com.exchange.scanner.dto.response.exchangedata.huobi.tradingfee;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HuobiFeeData {

    private String symbol;

    private String takerFeeRate;
}
