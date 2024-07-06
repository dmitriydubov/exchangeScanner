package com.exchange.scanner.dto.response.exchangedata.responsedata.coindepth;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class CoinDepth {

    private Integer statusCode;

    private String coinName;

    private Set<CoinDepthBid> coinDepthBids;

    private Set<CoinDepthAsk> coinDepthAsks;
}
