package com.exchange.scanner.dto.response.exchangedata.lbank.tradingfee;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LBankFeeData {

    @JsonProperty("takerCommission")
    private String takerCommission;
}
