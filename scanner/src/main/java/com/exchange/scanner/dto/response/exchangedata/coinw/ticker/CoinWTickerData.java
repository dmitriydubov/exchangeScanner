package com.exchange.scanner.dto.response.exchangedata.coinw.ticker;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinWTickerData {

    @JsonProperty("base-currency")
    private String baseCurrency;

    @JsonProperty("quote-currency")
    private String quoteCurrency;

    @JsonProperty("24Total")
    private String total24;

    private String latestDealPrice;

    @JsonProperty("fStatus")
    private Integer fStatus;
}
