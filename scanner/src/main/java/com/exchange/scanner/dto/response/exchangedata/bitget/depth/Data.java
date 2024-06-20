package com.exchange.scanner.dto.response.exchangedata.bitget.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Data {

    private List<List<String>> asks;

    private List<List<String>> bids;

    @JsonIgnore
    private String ts;

    @JsonIgnore
    private String scale;

    @JsonIgnore
    private String precision;

    @JsonIgnore
    private String isMaxPrecision;
}
