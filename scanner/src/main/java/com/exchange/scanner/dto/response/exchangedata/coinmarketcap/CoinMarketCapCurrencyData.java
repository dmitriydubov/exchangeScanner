package com.exchange.scanner.dto.response.exchangedata.coinmarketcap;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinMarketCapCurrencyData {

    private String symbol;

    private String slug;

    private String logo;
}
