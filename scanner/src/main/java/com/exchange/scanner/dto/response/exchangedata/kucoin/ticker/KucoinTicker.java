package com.exchange.scanner.dto.response.exchangedata.kucoin.ticker;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KucoinTicker {

    private String symbol;

    private String vol;

    private String buy;

    private String sell;
}
