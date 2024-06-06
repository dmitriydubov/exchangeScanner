package com.exchange.scanner.dto.response.exchangedata.bitget.ticker;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BitgetTickerData {

    private String symbol;

    private String baseVolume;

    private String bidPr;

    private String askPr;
}
