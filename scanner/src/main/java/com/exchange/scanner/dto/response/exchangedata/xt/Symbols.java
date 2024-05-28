package com.exchange.scanner.dto.response.exchangedata.xt;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Symbols {

    private String baseCurrency;

    private String state;

    private Boolean tradingEnabled;
}
