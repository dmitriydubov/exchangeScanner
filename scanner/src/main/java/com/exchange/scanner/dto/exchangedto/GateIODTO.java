package com.exchange.scanner.dto.exchangedto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GateIODTO {

    @JsonProperty("currency_pair")
    private String currencyPair;

    private String last;

    @JsonProperty("lowest_ask")
    private String lowestAsk;

    @JsonProperty("highest_bid")
    private String highestBid;

    @JsonProperty("change_percentage")
    private String changePercentage;

    @JsonProperty("change_utc0")
    private String changeUtc0;

    @JsonProperty("change_utc8")
    private String changeUtc8;

    @JsonProperty("base_volume")
    private String baseVolume;

    @JsonProperty("quote_volume")
    private String quoteVolume;

    @JsonProperty("high_24h")
    private String high24h;

    @JsonProperty("low_24h")
    private String low24h;

    @JsonProperty("etf_net_value")
    private String etfNetValue;

    @JsonProperty("etf_pre_net_value")
    private String etfPreNetValue;

    @JsonProperty("etf_pre_timestamp")
    private String etfPreTimestamp;

    @JsonProperty("etf_leverage")
    private String etfLeverage;
}
