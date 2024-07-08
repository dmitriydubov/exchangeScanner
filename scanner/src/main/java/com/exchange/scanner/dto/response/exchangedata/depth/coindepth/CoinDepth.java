package com.exchange.scanner.dto.response.exchangedata.depth.coindepth;

import lombok.Getter;
import lombok.Setter;

import java.util.TreeSet;

@Getter
@Setter
public class CoinDepth {

    private Integer statusCode;

    private String coinName;

    private TreeSet<CoinDepthBid> coinDepthBids;

    private TreeSet<CoinDepthAsk> coinDepthAsks;
}
