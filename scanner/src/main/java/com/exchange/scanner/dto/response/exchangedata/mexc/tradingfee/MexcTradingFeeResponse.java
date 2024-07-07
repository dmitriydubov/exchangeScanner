package com.exchange.scanner.dto.response.exchangedata.mexc.tradingfee;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class MexcTradingFeeResponse {

    @JsonIgnore
    private String coinName;

    private MexcFeeData data;
}
