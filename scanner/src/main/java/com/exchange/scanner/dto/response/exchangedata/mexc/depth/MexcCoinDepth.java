package com.exchange.scanner.dto.response.exchangedata.mexc.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MexcCoinDepth {

    @JsonIgnore
    private String lastUpdateId;

    @JsonIgnore
    private String coinName;

    private List<List<String>> bids;

    private List<List<String>> asks;
}
