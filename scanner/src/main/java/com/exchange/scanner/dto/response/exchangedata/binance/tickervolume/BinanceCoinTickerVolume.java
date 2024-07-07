package com.exchange.scanner.dto.response.exchangedata.binance.tickervolume;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BinanceCoinTickerVolume {

    private String symbol;

    private String quoteVolume;
}
