package com.exchange.scanner.dto.response.exchangedata.coinex.coins;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinExCurrencyData {

    @JsonProperty("base_ccy")
    private String baseCcy;

    @JsonProperty("quote_ccy")
    private String quoteCcy;

    @JsonProperty("is_margin_available")
    private Boolean isMarginAvailable;
}
