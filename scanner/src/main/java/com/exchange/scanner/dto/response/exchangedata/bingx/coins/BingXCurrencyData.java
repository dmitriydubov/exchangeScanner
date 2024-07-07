package com.exchange.scanner.dto.response.exchangedata.bingx.coins;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BingXCurrencyData {

    private List<BingXCurrency> symbols;
}
