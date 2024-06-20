package com.exchange.scanner.dto.response.exchangedata.xt.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Result {

    @JsonIgnore
    private Long timestamp;

    @JsonIgnore
    private Long lastUpdateId;

    private List<List<String>> bids;

    private List<List<String>> asks;
}
