package com.exchange.scanner.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MarketSettings {
    @JsonProperty("markets_buy")
    private List<String> marketsBuy;

    @JsonProperty("markets_sell")
    private List<String> marketsSell;

    private List<String> coins;
}
