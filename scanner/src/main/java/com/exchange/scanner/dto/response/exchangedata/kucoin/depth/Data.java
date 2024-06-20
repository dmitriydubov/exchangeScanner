package com.exchange.scanner.dto.response.exchangedata.kucoin.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Data {

    @JsonIgnore
    private Long time;

    @JsonIgnore
    private String sequence;

    List<List<String>> bids;

    List<List<String>> asks;
}
