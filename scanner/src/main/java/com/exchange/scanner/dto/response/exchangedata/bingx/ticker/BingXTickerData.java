package com.exchange.scanner.dto.response.exchangedata.bingx.ticker;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BingXTickerData {

    private String symbol;

    private String volume;

    private String bidPrice;

    private String askPrice;
}
