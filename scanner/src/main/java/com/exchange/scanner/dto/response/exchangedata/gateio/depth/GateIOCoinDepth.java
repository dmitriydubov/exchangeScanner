package com.exchange.scanner.dto.response.exchangedata.gateio.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GateIOCoinDepth {

    @JsonIgnore
    private String current;

    @JsonIgnore
    private String update;

    @JsonIgnore
    private String coinName;

    private List<List<String>> bids;

    private List<List<String>> asks;
}
