package com.exchange.scanner.dto.response.exchangedata.depth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CoinDataTicker {

    private String symbol;

    private String volume;

    private String bid;

    private String ask;
}
