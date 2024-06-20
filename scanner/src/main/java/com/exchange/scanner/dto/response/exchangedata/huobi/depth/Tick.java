package com.exchange.scanner.dto.response.exchangedata.huobi.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Tick {

    @JsonIgnore
    private Long ts;

    @JsonIgnore
    private Long version;

    private List<List<String>> bids;

    private List<List<String>> asks;
}
