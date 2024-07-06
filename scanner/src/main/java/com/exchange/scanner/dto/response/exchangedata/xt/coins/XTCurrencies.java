package com.exchange.scanner.dto.response.exchangedata.xt.coins;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class XTCurrencies {

    private String baseCurrency;

    private String quoteCurrency;

    private String state;

    private Boolean tradingEnabled;
}
