package com.exchange.scanner.dto.response.exchangedata.coinw.tickervolume;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinWVolumeTickerData {

    @JsonProperty("base-currency")
    private String baseCurrency;

    @JsonProperty("quote-currency")
    private String quoteCurrency;

    @JsonProperty("24Total")
    private String total24;

    private String latestDealPrice;
}
