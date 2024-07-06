package com.exchange.scanner.dto.response.exchangedata.coinex.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CoinExDepth {

    private List<List<String>> asks;

    private List<List<String>> bids;
}
