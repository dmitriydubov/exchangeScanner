package com.exchange.scanner.dto.response.exchangedata.poloniex.tickervolume;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PoloniexVolumeData {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("amount")
    private String amount;
}
