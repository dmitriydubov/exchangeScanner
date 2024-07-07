package com.exchange.scanner.dto.response.exchangedata.mexc.tickervolume;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MexcCoinTicker {

    private String symbol;

    private String quoteVolume;
}
