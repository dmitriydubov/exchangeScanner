package com.exchange.scanner.dto.response.exchangedata.coinw.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CoinWDepthData {

    private List<List<String>> bids;

    private List<List<String>> asks;
}
