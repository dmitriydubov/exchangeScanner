package com.exchange.scanner.dto.response.exchangedata.mexc.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MexcCoinDepth {

    private List<List<String>> bids;

    private List<List<String>> asks;
}
