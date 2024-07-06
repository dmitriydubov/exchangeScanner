package com.exchange.scanner.dto.response.exchangedata.probit.coins;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProbitCurrencyData {

    @JsonProperty("base_currency_id")
    private String baseCurrencyId;

    @JsonProperty("quote_currency_id")
    private String quoteCurrencyId;

    private Boolean closed;
}
