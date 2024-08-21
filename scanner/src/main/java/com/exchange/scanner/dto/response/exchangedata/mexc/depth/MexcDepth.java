package com.exchange.scanner.dto.response.exchangedata.mexc.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MexcDepth {

    private List<MexcBid> bids;

    private List<MexcAsk> asks;
}
