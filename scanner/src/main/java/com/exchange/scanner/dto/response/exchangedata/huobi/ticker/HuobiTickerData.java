package com.exchange.scanner.dto.response.exchangedata.huobi.ticker;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HuobiTickerData {

    private String symbol;

    private String vol;

    private String bid;

    private String ask;
}
