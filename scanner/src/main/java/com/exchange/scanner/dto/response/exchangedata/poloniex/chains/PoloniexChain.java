package com.exchange.scanner.dto.response.exchangedata.poloniex.chains;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PoloniexChain {

    @JsonProperty("blockchain")
    private String blockchain;

    @JsonProperty("withdrawalFee")
    private String withdrawalFee;
}
