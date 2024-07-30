package com.exchange.scanner.dto.response.exchangedata.mexc.tradingfee;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MexcTradingFeeSymbol {

    private String symbol;

    private String quoteAsset;

    private String takerCommission;
}
