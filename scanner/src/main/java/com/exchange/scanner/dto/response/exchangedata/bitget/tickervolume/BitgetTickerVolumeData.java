package com.exchange.scanner.dto.response.exchangedata.bitget.tickervolume;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BitgetTickerVolumeData {

    private String symbol;

    private String quoteVolume;
}
