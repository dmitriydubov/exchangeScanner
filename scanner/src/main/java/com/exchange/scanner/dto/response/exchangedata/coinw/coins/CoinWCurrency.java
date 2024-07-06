package com.exchange.scanner.dto.response.exchangedata.coinw.coins;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinWCurrency {

    private String currencyBase;

    private String currencyQuote;

    private Integer state;
}
