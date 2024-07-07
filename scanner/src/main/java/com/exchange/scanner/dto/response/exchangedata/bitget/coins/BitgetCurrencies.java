package com.exchange.scanner.dto.response.exchangedata.bitget.coins;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BitgetCurrencies {

    private String baseCoin;

    private String quoteCoin;

    private String status;
}
