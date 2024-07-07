package com.exchange.scanner.dto.response.exchangedata.gateio.volume24h;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GateIOCoinTickerVolume {

    @JsonProperty("currency_pair")
    private String currencyPair;

    @JsonProperty("quote_volume")
    private String quoteVolume;
}
