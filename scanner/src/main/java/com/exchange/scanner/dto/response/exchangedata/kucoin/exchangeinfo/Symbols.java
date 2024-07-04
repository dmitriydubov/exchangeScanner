package com.exchange.scanner.dto.response.exchangedata.kucoin.exchangeinfo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Symbols {

    private String baseCurrency;

    private String quoteCurrency;

    private Boolean enableTrading;
}
