package com.exchange.scanner.dto.response.exchangedata.bingx.tradingfee;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BingXTradingFeeResponse {

    private String code;

    private BingXFeeData data;
}
