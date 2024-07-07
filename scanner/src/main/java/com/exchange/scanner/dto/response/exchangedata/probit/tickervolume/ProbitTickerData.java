package com.exchange.scanner.dto.response.exchangedata.probit.tickervolume;

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
}
