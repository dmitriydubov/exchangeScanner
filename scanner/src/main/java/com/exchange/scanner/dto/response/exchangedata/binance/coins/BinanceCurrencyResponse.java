package com.exchange.scanner.dto.response.exchangedata.binance.coins;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BinanceCurrencyResponse {

    private List<BinanceCurrency> symbols;
}
