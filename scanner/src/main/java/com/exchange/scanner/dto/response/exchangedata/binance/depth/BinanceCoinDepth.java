package com.exchange.scanner.dto.response.exchangedata.binance.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BinanceCoinDepth {

    private String s;

    private List<List<String>> b;

    private List<List<String>> a;
}
