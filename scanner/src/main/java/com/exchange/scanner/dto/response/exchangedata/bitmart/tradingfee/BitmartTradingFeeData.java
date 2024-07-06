package com.exchange.scanner.dto.response.exchangedata.bitmart.tradingfee;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BitmartTradingFeeData {

    @JsonProperty("buy_taker_fee_rate")
    private String takerFee;
}
