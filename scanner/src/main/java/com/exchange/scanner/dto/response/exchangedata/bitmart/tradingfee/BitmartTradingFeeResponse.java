package com.exchange.scanner.dto.response.exchangedata.bitmart.tradingfee;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BitmartTradingFeeResponse {

    private String message;

    private String code;

    private BitmartTradingFeeData data;
}
