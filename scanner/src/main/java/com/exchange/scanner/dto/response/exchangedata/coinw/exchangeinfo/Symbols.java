package com.exchange.scanner.dto.response.exchangedata.coinw.exchangeinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Symbols {

    @JsonProperty("base-currency")
    private String baseCurrency;

    @JsonProperty("quote-currency")
    private String quoteCurrency;

    @JsonProperty("fStatus")
    private Integer fStatus;
}
