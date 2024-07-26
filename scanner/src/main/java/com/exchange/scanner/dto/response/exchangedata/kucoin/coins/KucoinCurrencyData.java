package com.exchange.scanner.dto.response.exchangedata.kucoin.coins;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KucoinCurrencyData {

    private String baseCurrency;

    private String quoteCurrency;

    private Boolean enableTrading;

    private Boolean isMarginEnabled;
}
