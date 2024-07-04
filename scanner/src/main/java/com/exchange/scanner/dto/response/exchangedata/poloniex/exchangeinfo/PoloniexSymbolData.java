package com.exchange.scanner.dto.response.exchangedata.poloniex.exchangeinfo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PoloniexSymbolData {

    private String baseCurrencyName;

    private String quoteCurrencyName;

    private String state;
}
