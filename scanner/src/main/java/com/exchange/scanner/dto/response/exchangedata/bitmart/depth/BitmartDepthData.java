package com.exchange.scanner.dto.response.exchangedata.bitmart.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BitmartDepthData {

    private List<List<String>> asks;

    private List<List<String>> bids;

    private String symbol;
}
