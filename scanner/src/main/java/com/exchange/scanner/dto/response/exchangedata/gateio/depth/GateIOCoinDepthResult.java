package com.exchange.scanner.dto.response.exchangedata.gateio.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GateIOCoinDepthResult {

    private String s;

    private List<List<String>> bids;

    private List<List<String>> asks;
}
