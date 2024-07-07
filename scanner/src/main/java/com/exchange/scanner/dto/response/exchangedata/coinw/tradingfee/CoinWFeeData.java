package com.exchange.scanner.dto.response.exchangedata.coinw.tradingfee;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinWFeeData {

    @JsonProperty("txFee")
    private String txFee;
}
