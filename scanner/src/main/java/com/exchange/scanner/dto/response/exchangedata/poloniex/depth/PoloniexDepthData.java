package com.exchange.scanner.dto.response.exchangedata.poloniex.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PoloniexDepthData {

    private String symbol;

    private List<List<String>> asks;

    private List<List<String>> bids;
}
