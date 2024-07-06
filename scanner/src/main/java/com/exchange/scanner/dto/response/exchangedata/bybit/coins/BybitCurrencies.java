package com.exchange.scanner.dto.response.exchangedata.bybit.coins;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BybitCurrencies {

    private String baseCoin;

    private String quoteCoin;

    private String showStatus;
}
