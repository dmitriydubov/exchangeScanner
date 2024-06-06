package com.exchange.scanner.dto.response.exchangedata.lbank.ticker;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LBankTickerData {

    private String symbol;

    private Ticker ticker;
}
