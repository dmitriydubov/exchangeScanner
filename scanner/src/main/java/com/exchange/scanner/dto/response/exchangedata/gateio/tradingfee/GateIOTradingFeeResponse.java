package com.exchange.scanner.dto.response.exchangedata.gateio.tradingfee;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GateIOTradingFeeResponse {

    @JsonProperty("taker_fee")
    private String takerFee;
}
