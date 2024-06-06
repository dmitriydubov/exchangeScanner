package com.exchange.scanner.dto.response.exchangedata.coinex.ticker;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinExTickerData {

    private String market;

    private String volume;

    private String last;
}
