package com.exchange.scanner.dto.response.exchangedata.bitmart.ticker;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BitmartTickerData {

    @JsonProperty(index = 0)
    private String symbol;

    @JsonProperty(index = 3)
    private String qv24h;

    @JsonProperty(index = 8)
    private String bidPx;

    @JsonProperty(index = 10)
    private String askPx;
}
