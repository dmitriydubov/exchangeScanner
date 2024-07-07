package com.exchange.scanner.dto.response.exchangedata.bitmart.coins;

import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
public class BitmartCurrencyData {

    private List<BitmartSymbol> symbols;
}
