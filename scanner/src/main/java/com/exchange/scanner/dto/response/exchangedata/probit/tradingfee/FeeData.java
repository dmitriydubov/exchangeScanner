package com.exchange.scanner.dto.response.exchangedata.probit.tradingfee;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeeData {

    private String id;

    @JsonProperty("base_currency_id")
    private String baseCurrencyId;

    @JsonProperty("taker_fee_rate")
    private String takerFeeRate;
}
