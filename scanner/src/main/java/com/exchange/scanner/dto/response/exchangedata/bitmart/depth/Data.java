package com.exchange.scanner.dto.response.exchangedata.bitmart.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Data {

    @JsonIgnore
    private String ts;

    private String symbol;

    private List<List<String>> asks;

    private List<List<String>> bids;
}
