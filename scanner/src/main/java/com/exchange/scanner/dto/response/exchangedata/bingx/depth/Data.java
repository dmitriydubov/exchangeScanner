package com.exchange.scanner.dto.response.exchangedata.bingx.depth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Data {

    private List<List<String>> bids;

    private List<List<String>> asks;

    @JsonIgnore
    private Long ts;
}
