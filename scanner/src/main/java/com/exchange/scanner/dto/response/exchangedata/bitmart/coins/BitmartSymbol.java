package com.exchange.scanner.dto.response.exchangedata.bitmart.coins;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BitmartSymbol {

    @JsonProperty("base_currency")
    private String baseCurrency;

    @JsonProperty("quote_currency")
    private String quoteCurrency;

    @JsonProperty("trade_status")
    private String tradeStatus;
}
