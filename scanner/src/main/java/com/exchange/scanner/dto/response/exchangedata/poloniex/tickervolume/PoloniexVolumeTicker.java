package com.exchange.scanner.dto.response.exchangedata.poloniex.tickervolume;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PoloniexVolumeTicker {

    private String symbol;

    private String amount;
}
