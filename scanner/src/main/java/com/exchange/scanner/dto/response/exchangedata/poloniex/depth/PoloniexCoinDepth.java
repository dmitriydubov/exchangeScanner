package com.exchange.scanner.dto.response.exchangedata.poloniex.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PoloniexCoinDepth {

    @JsonIgnore
    private String coinName;

    private List<String> asks;

    private List<String> bids;

}
