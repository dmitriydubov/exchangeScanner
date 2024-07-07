package com.exchange.scanner.dto.response.exchangedata.mexc.coins;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MexcCurrencySymbols {

    private String status;

    private String baseAsset;

    private String quoteAsset;
}
