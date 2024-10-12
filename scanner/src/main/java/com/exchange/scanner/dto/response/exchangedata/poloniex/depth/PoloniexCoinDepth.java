package com.exchange.scanner.dto.response.exchangedata.poloniex.depth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PoloniexCoinDepth {

    private List<PoloniexDepthData> data;
}
