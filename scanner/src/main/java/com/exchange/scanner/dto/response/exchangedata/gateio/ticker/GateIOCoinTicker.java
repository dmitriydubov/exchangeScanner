package com.exchange.scanner.dto.response.exchangedata.gateio.ticker;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GateIOCoinTicker {

    @JsonProperty("currency_pair")
    private String currencyPair;

    @JsonProperty("lowest_ask")
    private String lowestAsk;

    @JsonProperty("highest_bid")
    private String highestBid;

    @JsonProperty("quote_volume")
    private String quoteVolume;
}
