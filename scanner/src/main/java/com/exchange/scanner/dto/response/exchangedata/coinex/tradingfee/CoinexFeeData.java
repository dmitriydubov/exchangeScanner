package com.exchange.scanner.dto.response.exchangedata.coinex.tradingfee;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinexFeeData {

    @JsonProperty("taker_fee_rate")
    private String takerFeeRate;

    @JsonProperty("base_ccy")
    private String baseCcy;

    @JsonProperty("quote_ccy")
    private String quoteCcy;
}
