package com.exchange.scanner.dto.response.exchangedata.coinex.exchangeinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Symbols {

    @JsonProperty("base_ccy")
    private String baseCcy;

    @JsonProperty("quote_ccy")
    private String quoteCcy;
}
