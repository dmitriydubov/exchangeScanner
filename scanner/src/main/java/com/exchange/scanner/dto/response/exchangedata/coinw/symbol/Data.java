package com.exchange.scanner.dto.response.exchangedata.coinw.symbol;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class Data {

    @JsonProperty("交易对symbol")
    private String symbol;
}
