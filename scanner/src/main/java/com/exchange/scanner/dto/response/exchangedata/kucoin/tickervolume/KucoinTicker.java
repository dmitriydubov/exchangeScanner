package com.exchange.scanner.dto.response.exchangedata.kucoin.tickervolume;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KucoinTicker {

    private String symbol;

    private String volValue;
}
