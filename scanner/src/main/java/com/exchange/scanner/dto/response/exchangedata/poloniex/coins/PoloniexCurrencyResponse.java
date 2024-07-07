package com.exchange.scanner.dto.response.exchangedata.poloniex.coins;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PoloniexCurrencyResponse {

    private String baseCurrencyName;

    private String quoteCurrencyName;

    private String state;
}
