package com.exchange.scanner.dto.response.exchangedata.probit.ticker;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProbitTickerData {

    @JsonProperty("market_id")
    private String marketId;

    @JsonProperty("quote_volume")
    private String quoteVolume;

    @JsonProperty("last")
    private String last;
}
