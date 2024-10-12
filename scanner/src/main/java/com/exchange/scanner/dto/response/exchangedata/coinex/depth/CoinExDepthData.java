package com.exchange.scanner.dto.response.exchangedata.coinex.depth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinExDepthData {

    private String market;

    private CoinExDepth depth;
}
