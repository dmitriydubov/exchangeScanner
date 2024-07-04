package com.exchange.scanner.dto.response.exchangedata.probit.exchangeinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Symbols {

    @JsonProperty("base_currency_id")
    private String baseCurrencyId;

    @JsonProperty("quote_currency_id")
    private String quoteCurrencyId;

    private Boolean closed;
}
