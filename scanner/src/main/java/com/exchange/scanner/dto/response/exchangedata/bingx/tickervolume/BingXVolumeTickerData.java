package com.exchange.scanner.dto.response.exchangedata.bingx.tickervolume;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BingXVolumeTickerData {

    private String symbol;

    private String quoteVolume;
}
