package com.exchange.scanner.dto.response.exchangedata.kucoin.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KucoinDepthData {

    private List<List<String>> bids;

    private List<List<String>> asks;
}
